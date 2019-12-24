/*
 * stm32WindowSensor: RF window sensors: STM32L + RFM69 + Android
 * 
 * Copyright (C) 2019. Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program.
 */

#include <cstring>
#include <cstdlib>

#include "BasicIO.h"

using namespace StmPlusPlus;

#define USART_DEBUG_MODULE "COMM: "

/************************************************************************
 * Class IOPort
 ************************************************************************/

IOPort::IOPort (PortName name, uint32_t mode, uint32_t pull, uint32_t speed,
                uint32_t pin/* = GPIO_PIN_All*/, bool callInit/* = true*/, bool value/* = false*/)
{
    gpioParameters.Pin = pin;
    gpioParameters.Mode = mode;
    gpioParameters.Pull = pull;
    gpioParameters.Speed = speed;
    
    switch (name)
    {
    case A:
        __GPIOA_CLK_ENABLE();
        port = GPIOA;
        break;
    case B:
        __GPIOB_CLK_ENABLE();
        port = GPIOB;
        break;
    case C:
        __GPIOC_CLK_ENABLE();
        port = GPIOC;
        break;
    }

    if (callInit)
    {
        HAL_GPIO_DeInit(port, pin);
        if (mode == GPIO_MODE_OUTPUT_PP)
        {
            HAL_GPIO_WritePin(port, gpioParameters.Pin, (GPIO_PinState) value);
        }
        HAL_GPIO_Init(port, &gpioParameters);
    }
}

void IOPin::activateClockOutput (uint32_t source, uint32_t div)
{
    gpioParameters.Mode = GPIO_MODE_AF_PP;
    gpioParameters.Pull = GPIO_NOPULL;
    gpioParameters.Speed = GPIO_SPEED_FREQ_LOW;
    gpioParameters.Alternate = GPIO_AF0_MCO;
    HAL_GPIO_Init(GPIOA, &gpioParameters);
    HAL_RCC_MCOConfig(RCC_MCO1, source, div);
}

/************************************************************************
 * Class Usart
 ************************************************************************/

#ifdef STM32L0
#define GPIO_AF7_USART1 GPIO_AF4_USART1
#define GPIO_AF7_USART2 GPIO_AF4_USART2
#endif

Usart::Usart (DeviceName _device, PortName name, uint32_t txPin, uint32_t rxPin, uint32_t speed) :
        IOPort(name, GPIO_MODE_AF_PP, GPIO_PULLUP, speed, txPin | rxPin, false),
        device(_device)
{
    switch (device)
    {
    case USART_1:
        setAlternate(GPIO_AF7_USART1);
        usartParameters.Instance = USART1;
        irqName = USART1_IRQn;
        break;
        
    case USART_2:
        setAlternate(GPIO_AF7_USART2);
        usartParameters.Instance = USART2;
        irqName = USART2_IRQn;
        break;
    }
    
    usartParameters.Init.HwFlowCtl = UART_HWCONTROL_NONE;
    usartParameters.Init.OverSampling = UART_OVERSAMPLING_16;
    #ifdef UART_ONE_BIT_SAMPLE_DISABLE
    usartParameters.Init.OneBitSampling = UART_ONE_BIT_SAMPLE_DISABLE;
    #endif
    #ifdef UART_ADVFEATURE_NO_INIT
    usartParameters.AdvancedInit.AdvFeatureInit = UART_ADVFEATURE_NO_INIT;
    #endif
    irqStatus = RESET;
}

void Usart::enableClock ()
{
    switch (device)
    {
    case USART_1:
        __HAL_RCC_USART1_CLK_ENABLE();
        break;
    case USART_2:
        __HAL_RCC_USART2_CLK_ENABLE();
        break;
    }
}

void Usart::disableClock ()
{
    switch (device)
    {
    case USART_1:
        __HAL_RCC_USART1_CLK_DISABLE();
        break;
    case USART_2:
        __HAL_RCC_USART2_CLK_DISABLE();
        break;
    }
}

HAL_StatusTypeDef Usart::start (uint32_t mode, uint32_t baudRate, uint32_t wordLength/* = UART_WORDLENGTH_8B*/,
                                uint32_t stopBits/* = UART_STOPBITS_1*/, uint32_t parity/* = UART_PARITY_NONE*/)
{
    enableClock();
    usartParameters.Init.Mode = mode;
    usartParameters.Init.BaudRate = baudRate;
    usartParameters.Init.WordLength = wordLength;
    usartParameters.Init.StopBits = stopBits;
    usartParameters.Init.Parity = parity;
    return HAL_UART_Init(&usartParameters);
}

HAL_StatusTypeDef Usart::stop ()
{
    HAL_StatusTypeDef retValue = HAL_UART_DeInit(&usartParameters);
    disableClock();
    return retValue;
}

HAL_StatusTypeDef Usart::transmit (const char * buffer, size_t n, uint32_t timeout)
{
    return HAL_UART_Transmit(&usartParameters, (unsigned char *) buffer, n, timeout);
}

HAL_StatusTypeDef Usart::transmit (const char * buffer)
{
    return HAL_UART_Transmit(&usartParameters, (unsigned char *) buffer, ::strlen(buffer), 0xFFFF);
}

HAL_StatusTypeDef Usart::receive (const char * buffer, size_t n, uint32_t timeout)
{
    return HAL_UART_Receive(&usartParameters, (unsigned char *) buffer, n, timeout);
}

HAL_StatusTypeDef Usart::transmitIt (const char * buffer, size_t n)
{
    irqStatus = RESET;
    return HAL_UART_Transmit_IT(&usartParameters, (unsigned char *) buffer, n);
}

HAL_StatusTypeDef Usart::receiveIt (const char * buffer, size_t n)
{
    irqStatus = RESET;
    return HAL_UART_Receive_IT(&usartParameters, (unsigned char *) buffer, n);
}

/************************************************************************
 * Class UsartLogger
 ************************************************************************/

UsartLogger * UsartLogger::instance = NULL;

UsartLogger::UsartLogger (DeviceName device, PortName name, uint32_t txPin, uint32_t rxPin, uint32_t speed, uint32_t _baudRate) :
        Usart(device, name, txPin, rxPin, speed),
        baudRate(_baudRate),
        radix(10)
{
    // empty
}

UsartLogger & UsartLogger::operator << (const char * buffer)
{
    transmit(buffer);
    return *this;
}

UsartLogger & UsartLogger::operator << (int n)
{
    char buffer[1024];
    if (radix == 16)
    {
        transmit("0x");
    }
    ::__itoa(n, buffer, radix);
    transmit(buffer);
    return *this;
}

UsartLogger & UsartLogger::operator << (Manupulator m)
{
    switch (m)
    {
    case Manupulator::ENDL:
        transmit("\n\r");
        break;
    case Manupulator::TAB:
        transmit("    ");
        break;
    case Manupulator::DEC:
        radix = 10;
        break;
    case Manupulator::HEX:
        radix = 16;
        break;
    }
    return *this;
}


/************************************************************************
 * Class Spi
 ************************************************************************/

Spi::Spi (DeviceName _device,
          IOPort::PortName sckPort,  uint32_t sckPin,
          IOPort::PortName misoPort, uint32_t misoPin,
          IOPort::PortName mosiPort, uint32_t mosiPin,
          uint32_t pull, uint32_t speed):
    device(_device),
    sck(sckPort,   sckPin,  GPIO_MODE_OUTPUT_PP, pull, speed, true, false),
    miso(misoPort, misoPin, GPIO_MODE_OUTPUT_PP, GPIO_NOPULL, speed, true, false),
    mosi(mosiPort, mosiPin, GPIO_MODE_OUTPUT_PP, pull, speed, true, false),
    hspi(NULL)
{
    switch (device)
    {
    case SPI_1:
        sck.setAlternate(GPIO_AF0_SPI1);
        miso.setAlternate(GPIO_AF0_SPI1);
        mosi.setAlternate(GPIO_AF0_SPI1);
        spiParams.Instance = SPI1;
        break;
    case SPI_2:
        sck.setAlternate(GPIO_AF5_SPI2);
        miso.setAlternate(GPIO_AF5_SPI2);
        mosi.setAlternate(GPIO_AF5_SPI2);
        spiParams.Instance = SPI2;
        break;
    }

    spiParams.Init.Mode = SPI_MODE_MASTER;
    spiParams.Init.DataSize = SPI_DATASIZE_8BIT;
    spiParams.Init.CLKPolarity = SPI_POLARITY_HIGH;
    spiParams.Init.CLKPhase = SPI_PHASE_2EDGE;
    spiParams.Init.FirstBit = SPI_FIRSTBIT_MSB;
    spiParams.Init.TIMode = SPI_TIMODE_DISABLE;
    spiParams.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
    spiParams.Init.CRCPolynomial = 7;
    spiParams.Init.NSS = SPI_NSS_SOFT;
}


void Spi::enableClock()
{
    switch (device)
    {
    case SPI_1: __HAL_RCC_SPI1_CLK_ENABLE(); break;
    case SPI_2: __HAL_RCC_SPI2_CLK_ENABLE(); break;
    }
}


void Spi::disableClock()
{
    switch (device)
    {
    case SPI_1: __HAL_RCC_SPI1_CLK_DISABLE(); break;
    case SPI_2: __HAL_RCC_SPI2_CLK_DISABLE(); break;
    }
}


HAL_StatusTypeDef Spi::start (uint32_t direction, uint32_t prescaler, uint32_t dataSize, uint32_t CLKPhase)
{
    sck.setMode(GPIO_MODE_AF_PP);
    miso.setMode(GPIO_MODE_AF_OD);
    mosi.setMode(GPIO_MODE_AF_PP);

    hspi = &spiParams;
    enableClock();

    spiParams.Init.Direction = direction;
    spiParams.Init.BaudRatePrescaler = prescaler;
    spiParams.Init.DataSize = dataSize;
    spiParams.Init.CLKPhase = CLKPhase;
    HAL_StatusTypeDef status = HAL_SPI_Init(hspi);
    if (status != HAL_OK)
    {
        USART_DEBUG("Can not initialize SPI " << (size_t)device << ": " << status);
        return status;
    }

    /* Configure communication direction : 1Line */
    if (spiParams.Init.Direction == SPI_DIRECTION_1LINE)
    {
        SPI_1LINE_TX(hspi);
    }

    /* Check if the SPI is already enabled */
    if ((spiParams.Instance->CR1 & SPI_CR1_SPE) != SPI_CR1_SPE)
    {
        /* Enable SPI peripheral */
        __HAL_SPI_ENABLE(hspi);
    }

    return status;
}


HAL_StatusTypeDef Spi::stop ()
{
    HAL_StatusTypeDef retValue = HAL_SPI_DeInit(&spiParams);
    disableClock();
    hspi = NULL;
    sck.setMode(GPIO_MODE_OUTPUT_PP);    sck.setLow();
    miso.setMode(GPIO_MODE_OUTPUT_PP);   miso.setLow();
    mosi.setMode(GPIO_MODE_OUTPUT_PP);   mosi.setLow();
    return retValue;
}


/************************************************************************
 * Class AnalogToDigitConverter
 ************************************************************************/

AnalogToDigitConverter::AnalogToDigitConverter (uint32_t channel)
{
    adcParams.Instance = ADC1;
    adcParams.Init.OversamplingMode = DISABLE;
    adcParams.Init.ClockPrescaler = ADC_CLOCK_SYNC_PCLK_DIV2;
    adcParams.Init.Resolution = ADC_RESOLUTION_12B;
    adcParams.Init.SamplingTime = ADC_SAMPLETIME_7CYCLES_5;
    adcParams.Init.ScanConvMode = ADC_SCAN_DIRECTION_FORWARD;
    adcParams.Init.DataAlign = ADC_DATAALIGN_RIGHT;
    adcParams.Init.ContinuousConvMode = DISABLE;
    adcParams.Init.DiscontinuousConvMode = DISABLE;
    adcParams.Init.ExternalTrigConv  = ADC_EXTERNALTRIGCONV_T2_TRGO;
    adcParams.Init.ExternalTrigConvEdge = ADC_EXTERNALTRIGCONVEDGE_NONE;
    adcParams.Init.DMAContinuousRequests = DISABLE;
    adcParams.Init.EOCSelection = ADC_EOC_SINGLE_CONV;
    adcParams.Init.Overrun = ADC_OVR_DATA_PRESERVED;
    adcParams.Init.LowPowerAutoWait = DISABLE;
    adcParams.Init.LowPowerFrequencyMode = DISABLE;
    adcParams.Init.LowPowerAutoPowerOff = DISABLE;

    adcChannel.Channel = channel; // each channel is connected to the specific pin, see pin descriptions
    adcChannel.Rank = ADC_RANK_CHANNEL_NUMBER;
}


void AnalogToDigitConverter::enableClock()
{
    __HAL_RCC_ADC1_CLK_ENABLE();
}


void AnalogToDigitConverter::disableClock()
{
    __HAL_RCC_ADC1_CLK_DISABLE();
}


HAL_StatusTypeDef AnalogToDigitConverter::start ()
{
    ADC_HandleTypeDef * hadc = &adcParams;

    enableClock();

    HAL_StatusTypeDef halStatus = HAL_ADC_Init(hadc);
    if (halStatus != HAL_OK)
    {
        return halStatus;
    }

    halStatus = HAL_ADC_ConfigChannel(hadc, &adcChannel);
    if (halStatus != HAL_OK)
    {
        return halStatus;
    }

    return halStatus;
}


void AnalogToDigitConverter::stop ()
{
    ADC_HandleTypeDef * hadc = &adcParams;
    HAL_ADC_DeInit(hadc);
    disableClock();
}


uint32_t AnalogToDigitConverter::getValue ()
{
    uint32_t value = INVALID_VALUE;
    HAL_ADC_Start(&adcParams);
    if (HAL_ADC_PollForConversion(&adcParams, 100) == HAL_OK)
    {
        value = HAL_ADC_GetValue(&adcParams);
    }
    HAL_ADC_Stop(&adcParams);
    return value;
}


/************************************************************************
 * Class TimerBase
 ************************************************************************/
#define INITIALIZE_STMPLUSPLUS_TIMER(name, enableName) { \
        enableName(); \
        timerParameters.Instance = name; \
        }

TimerBase::TimerBase (TimerName timerName)
{
    switch (timerName)
    {
    case TIM_1:
        #ifdef TIM1
        INITIALIZE_STMPLUSPLUS_TIMER(TIM1, __TIM1_CLK_ENABLE);
        #endif
        break;
    case TIM_2:
        #ifdef TIM2
        INITIALIZE_STMPLUSPLUS_TIMER(TIM2, __TIM2_CLK_ENABLE);
        #endif
        break;
    case TIM_3:
        #ifdef TIM3
        INITIALIZE_STMPLUSPLUS_TIMER(TIM3, __TIM3_CLK_ENABLE);
        #endif
        break;
    case TIM_4:
        #ifdef TIM4
        INITIALIZE_STMPLUSPLUS_TIMER(TIM4, __TIM4_CLK_ENABLE);
        #endif
        break;
    case TIM_5:
        #ifdef TIM5
        INITIALIZE_STMPLUSPLUS_TIMER(TIM5, __TIM5_CLK_ENABLE);
        #endif
        break;
    case TIM_6:
        #ifdef TIM6
        INITIALIZE_STMPLUSPLUS_TIMER(TIM6, __TIM6_CLK_ENABLE);
        #endif
        break;
    case TIM_7:
        #ifdef TIM7
        INITIALIZE_STMPLUSPLUS_TIMER(TIM7, __TIM7_CLK_ENABLE);
        #endif
        break;
    case TIM_8:
        #ifdef TIM8
        INITIALIZE_STMPLUSPLUS_TIMER(TIM8, __TIM8_CLK_ENABLE);
        #endif
        break;
    case TIM_9:
        #ifdef TIM9
        INITIALIZE_STMPLUSPLUS_TIMER(TIM9, __TIM9_CLK_ENABLE);
        #endif
        break;
    case TIM_10:
        #ifdef TIM10
        INITIALIZE_STMPLUSPLUS_TIMER(TIM10, __TIM10_CLK_ENABLE);
        #endif
        break;
    case TIM_11:
        #ifdef TIM11
        INITIALIZE_STMPLUSPLUS_TIMER(TIM11, __TIM11_CLK_ENABLE);
        #endif
        break;
    case TIM_12:
        #ifdef TIM12
        INITIALIZE_STMPLUSPLUS_TIMER(TIM12, __TIM12_CLK_ENABLE);
        #endif
        break;
    case TIM_13:
        #ifdef TIM13
        INITIALIZE_STMPLUSPLUS_TIMER(TIM13, __TIM13_CLK_ENABLE);
        #endif
        break;
    case TIM_14:
        #ifdef TIM10
        INITIALIZE_STMPLUSPLUS_TIMER(TIM14, __TIM14_CLK_ENABLE);
        #endif
        break;
    case TIM_15:
        #ifdef TIM15
        INITIALIZE_STMPLUSPLUS_TIMER(TIM15, __TIM15_CLK_ENABLE);
        #endif
        break;
    case TIM_16:
        #ifdef TIM16
        INITIALIZE_STMPLUSPLUS_TIMER(TIM16, __TIM16_CLK_ENABLE);
        #endif
        break;
    case TIM_17:
        #ifdef TIM17
        INITIALIZE_STMPLUSPLUS_TIMER(TIM17, __TIM17_CLK_ENABLE);
        #endif
        break;
    }
}

HAL_StatusTypeDef TimerBase::startCounter (uint32_t counterMode, uint32_t prescaler, uint32_t period,
                                           uint32_t clockDivision/* = TIM_CLOCKDIVISION_DIV1*/)
{
    timerParameters.Init.CounterMode = counterMode;
    timerParameters.Init.Prescaler = prescaler;
    timerParameters.Init.Period = period;
    timerParameters.Init.ClockDivision = clockDivision;
    __HAL_TIM_SET_COUNTER(&timerParameters, 0);

    HAL_TIM_Base_Init(&timerParameters);
    return HAL_TIM_Base_Start(&timerParameters);
}

HAL_StatusTypeDef TimerBase::stopCounter ()
{
    HAL_TIM_Base_Stop(&timerParameters);
    return HAL_TIM_Base_DeInit(&timerParameters);
}

/************************************************************************
 * Class PulseWidthModulation
 ************************************************************************/
PulseWidthModulation::PulseWidthModulation (IOPort::PortName name, uint32_t _pin, uint32_t speed, TimerName timerName,
                                            uint32_t _channel) :
        TimerBase(timerName),
        pin(name, _pin, GPIO_MODE_OUTPUT_PP, GPIO_NOPULL, speed, false, false),
        channel(_channel)
{
    switch (timerName)
    {
    case TIM_2:
        #ifdef GPIO_AF2_TIM2
        pin.setAlternate(GPIO_AF2_TIM2);
        #endif
        break;
    default:
        break;
    }

    timerParameters.Init.Prescaler = 0;
    timerParameters.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
    timerParameters.Init.CounterMode = TIM_COUNTERMODE_UP;
    channelParameters.OCMode = TIM_OCMODE_PWM1;
    channelParameters.OCPolarity = TIM_OCPOLARITY_HIGH;
    channelParameters.OCFastMode = TIM_OCFAST_DISABLE;
}

bool PulseWidthModulation::start (uint32_t freq, uint32_t duty)
{
    uint32_t uwPeriodValue = SystemCoreClock / freq;
    timerParameters.Init.Period = uwPeriodValue - 1;
    channelParameters.Pulse = (duty * uwPeriodValue) / 100;

    pin.setMode(GPIO_MODE_AF_PP);

    HAL_StatusTypeDef status = HAL_TIM_PWM_Init(&timerParameters);
    if (status != HAL_OK)
    {
        USART_DEBUG("Cannot initialize PWM timer: " << status);
        return false;
    }

    status = HAL_TIM_PWM_ConfigChannel(&timerParameters, &channelParameters, channel);
    if (status != HAL_OK)
    {
        USART_DEBUG("Cannot initialize PWM channel: " << status);
        return false;
    }

    status = HAL_TIM_PWM_Start(&timerParameters, channel);
    if (status != HAL_OK)
    {
        USART_DEBUG("Cannot start PWM: " << status);
        return false;
    }
    return true;
}

void PulseWidthModulation::stop ()
{
    HAL_TIM_PWM_Stop(&timerParameters, channel);
    HAL_TIM_PWM_DeInit(&timerParameters);
    pin.setMode(GPIO_MODE_OUTPUT_PP);
    pin.setLow();
}
