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

#ifndef SENSOR_BASE_H_
#define SENSOR_BASE_H_

#include "RFM69.h"

#include <algorithm>
#include <array>

using namespace StmPlusPlus;

class SensorBase
{
public:

    enum DataType
    {
        STARTUP = 1,
        WAIT_FOR_READY = 2,
        ERROR = 3,
        SENSOR_DATA = 4,
        STANDBY = 5
    };

    // SYS_WKUP1 (IOPort::A, GPIO_PIN_0) is used as wakeup trigger
    static const uint16_t PIN_SWITH_VALUE = GPIO_PIN_1;
    static const IRQn_Type EVENT_IRQN = EXTI0_1_IRQn;

    static constexpr uint8_t BASE_ID = 1;
    static constexpr uint8_t PACKET_SIZE = 7;
    static constexpr size_t ADC_BUFFER_LENGTH = 5;
    static constexpr size_t MIN_VOLTAGE = 18;
    static constexpr size_t REPEAT_ON_ERROR_COUNT = 10;
    static constexpr uint32_t REPEAT_ON_ERROR_DELAY = 1000;

    // Magnetic switch
    __IO uint32_t switchChanges;

    // Voltage
    AnalogToDigitConverter adc;

    // RFM69 on SPI
    IOPin pinRfmPower, pinRfmCs, pinRfmDio0;
    Spi spi;
    RFM69 rfm;

    // LEDs
    PulseWidthModulation pinLed;

    SensorBase () :
        switchChanges(1),

        // Voltage
        adc(ADC_CHANNEL_VREFINT),

        // RFM69 on SPI
        pinRfmPower(IOPort::A, GPIO_PIN_3, GPIO_MODE_OUTPUT_PP, GPIO_NOPULL, GPIO_SPEED_FREQ_LOW, true, true),
        pinRfmCs(IOPort::A, GPIO_PIN_4, GPIO_MODE_OUTPUT_PP, GPIO_NOPULL, GPIO_SPEED_FREQ_LOW, true, false),
        pinRfmDio0(IOPort::A, GPIO_PIN_11, GPIO_MODE_INPUT, GPIO_NOPULL, GPIO_SPEED_FREQ_LOW),
        spi(Spi::SPI_1, /*SCK*/ IOPort::A, GPIO_PIN_5,
                        /* MI*/ IOPort::A, GPIO_PIN_6,
                        /* MO*/ IOPort::A, GPIO_PIN_7,
                        GPIO_NOPULL, GPIO_SPEED_FREQ_LOW),
        rfm(spi, pinRfmCs, pinRfmDio0),

        // LEDs
        pinLed(IOPort::A, GPIO_PIN_2, GPIO_SPEED_FREQ_LOW, TimerBase::TIM_2, TIM_CHANNEL_3)
    {
        // Activate interrupts for magnetic switch and busy line
        HAL_NVIC_SetPriority(EVENT_IRQN, 1, 0);
        HAL_NVIC_EnableIRQ(EVENT_IRQN);
    }

    /*
     * Wake Up configuration.
     */
    static void initWakeUp (void)
    {
        // Enable Ultra low power mode
        HAL_PWREx_EnableUltraLowPower();

        // Enable the fast wake up from Ultra low power mode
        HAL_PWREx_EnableFastWakeUp();

        // Check and handle if the system was resumed from Standby mode
        if (__HAL_PWR_GET_FLAG(PWR_FLAG_SB) != RESET)
        {
            // Clear Standby flag
            __HAL_PWR_CLEAR_FLAG(PWR_FLAG_SB);
        }

        // Disable all used wakeup sources: Pin1(PA.0)
        HAL_PWR_DisableWakeUpPin(PWR_WAKEUP_PIN1);

        // Clear all related wakeup flags
        __HAL_PWR_CLEAR_FLAG(PWR_FLAG_WU);
    }

    void processEXTI0_1_IRQn ()
    {
        if (__HAL_GPIO_EXTI_GET_FLAG(PIN_SWITH_VALUE))
        {
            ++switchChanges;
        }
        HAL_GPIO_EXTI_IRQHandler(PIN_SWITH_VALUE);
    }

    uint8_t CRC7_one(uint8_t crcIn, uint8_t data)
    {
        const uint8_t g = 0x89;
        uint8_t i;
        crcIn ^= data;
        for (i = 0; i < 8; i++)
        {
            if (crcIn & 0x80)
                crcIn ^= g;
            crcIn <<= 1;
        }
        return crcIn;
    }

    uint8_t CRC7_buf(uint8_t *pBuf, uint8_t len)
    {
        uint8_t crc = 0;
        while (len--)
            crc = CRC7_one(crc, *pBuf++);
        return crc;
    }

    uint8_t getMedianVoltage ()
    {
        std::array<uint32_t, ADC_BUFFER_LENGTH> adcBuffer;
        adcBuffer.fill(0);

        for (size_t i = 0; i < ADC_BUFFER_LENGTH; ++i)
        {
            if (adc.start() == HAL_OK)
            {
                adcBuffer[i] = adc.getValue();
            }
            adc.stop();
        }

        std::sort(adcBuffer.begin(), adcBuffer.end());
        float m = adcBuffer[ADC_BUFFER_LENGTH/2];
        return (uint8_t)(29.0 * 1820.0 / m);
    }

    uint32_t getLedPwm (uint8_t vdd)
    {
        uint32_t pwmMv = std::min(std::max((uint32_t)vdd, (uint32_t)18), (uint32_t)30);
        uint32_t pwm = 10 + 90 * (30 - pwmMv) / (30 - 18);
        return pwm;
    }

    void stopRfm()
    {
        pinRfmPower.setHigh();
        pinRfmCs.setLow();
        spi.stop();
    }

    bool initRfm (uint8_t id, uint32_t pwm)
    {
        for (size_t i = 0; i < REPEAT_ON_ERROR_COUNT; i++)
        {
            pinRfmCs.setHigh();
            pinRfmPower.setLow();
            spi.start(SPI_DIRECTION_2LINES, SPI_BAUDRATEPRESCALER_4, SPI_DATASIZE_8BIT, SPI_PHASE_2EDGE);
            if (rfm.initialize(RF69_433MHZ, id, 1))
            {
                rfm.setHighPower(true);
                return true;
            }
            else
            {
                pinLed.start(100, pwm);
                stopRfm();
                HAL_Delay(REPEAT_ON_ERROR_DELAY);
                pinLed.stop();
            }
        }
        return false;
    }

    void enterStandBy (bool _stopRfm = true)
    {
        // The Following Wakeup sequence is highly recommended prior to each Standby mode entry
        //   mainly  when using more than one wakeup source this is to not miss any wakeup event.
        //   - Disable all used wakeup sources,
        //   - Clear all related wakeup flags,
        //  - Re-enable all used wakeup sources,
        //   - Enter the Standby mode
        if (_stopRfm)
        {
            stopRfm();
        }

        HAL_PWR_EnableWakeUpPin(PWR_WAKEUP_PIN1);
        HAL_PWR_EnterSTANDBYMode();
    }
};


#endif
