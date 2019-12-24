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

#ifndef SENSOR_GATEWAY_H_
#define SENSOR_GATEWAY_H_

#ifndef SENSOR_LOGIC

#include "SensorBase.h"

#include <cstring>
#include <cstdlib>

using namespace StmPlusPlus;

#define USART_DEBUG_MODULE "GW;"

class SensorGateway : public SensorBase
{
private:

    // Data packet
    uint8_t packet[PACKET_SIZE];

    // Logging
    UsartLogger log;

public:
    
    SensorGateway () :
        SensorBase(),

        // Logging
        log(Usart::USART_1, IOPort::A, GPIO_PIN_9, GPIO_PIN_10, GPIO_SPEED_FREQ_HIGH, 2000000)
    {
        // empty
    }
    
    /*
     * System Clock Configuration
     */
    static void initSystemClock (void)
    {
        RCC_OscInitTypeDef RCC_OscInitStruct;
        RCC_ClkInitTypeDef RCC_ClkInitStruct;

        __HAL_RCC_PWR_CLK_ENABLE();
        __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE1);

        RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
        RCC_OscInitStruct.HSIState = RCC_HSI_DIV4;
        RCC_OscInitStruct.HSICalibrationValue = 16;
        RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
        RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSI;
        RCC_OscInitStruct.PLL.PLLMUL = RCC_PLLMUL_16;
        RCC_OscInitStruct.PLL.PLLDIV = RCC_PLLDIV_2;
        HAL_RCC_OscConfig(&RCC_OscInitStruct);

        RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK | RCC_CLOCKTYPE_SYSCLK | RCC_CLOCKTYPE_PCLK1
                                      | RCC_CLOCKTYPE_PCLK2;
        RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
        RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
        RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
        RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV2;
        HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_1);

        RCC_PeriphCLKInitTypeDef PeriphClkInit;
        PeriphClkInit.PeriphClockSelection = RCC_PERIPHCLK_USART1;
        PeriphClkInit.Usart1ClockSelection = RCC_USART1CLKSOURCE_SYSCLK;
        HAL_RCCEx_PeriphCLKConfig(&PeriphClkInit);

        HAL_SYSTICK_Config(HAL_RCC_GetHCLKFreq() / 1000);
        HAL_SYSTICK_CLKSourceConfig(SYSTICK_CLKSOURCE_HCLK);
        HAL_NVIC_SetPriority(SysTick_IRQn, 0, 0);
    }
    
    void run ()
    {
        log.initInstance();

        uint8_t vdd = getMedianVoltage();
        uint32_t pwm = getLedPwm(vdd);

        USART_DEBUG((int)DataType::STARTUP << ";" << SystemCoreClock << ";" << vdd << ".");

        if (!initRfm(BASE_ID, pwm))
        {
            USART_DEBUG((int)DataType::ERROR << ";" << BASE_ID << ";Can not initialize RFM.");
            enterStandBy();
        }

        listenSensors(pwm);
    }

    void listenSensors(uint32_t pwm)
    {
        RFM69::Payload state;
        uint8_t crc;
        WordToBytes reqId;

        rfm.receiveBegin();

        while (true)
        {
            if (rfm.waitForResponce(pinRfmDio0, state, 100))
            {
                if (state.size < PACKET_SIZE + 3)
                {
                    USART_DEBUG((int)DataType::ERROR << ";" << state.senderId << ";Invalid message size.");
                    continue;
                }

                crc = CRC7_buf(state.data, PACKET_SIZE - 2);
                if (crc != state.data[PACKET_SIZE - 2])
                {
                    USART_DEBUG((int)DataType::ERROR << ";" << state.senderId << ";Invalid CRC.");
                    continue;
                }

                reqId.bytes.low = state.data[1];
                reqId.bytes.high = state.data[2];

                packet[0] = 128 + state.data[0];
                packet[1] = reqId.bytes.low;
                packet[2] = reqId.bytes.high;
                packet[3] = state.data[3];
                packet[4] = state.data[4];
                packet[5] = CRC7_buf(packet, PACKET_SIZE - 2);
                packet[6] = 0xFF;

                if (state.ackRequested())
                {
                    pinLed.start(100, pwm);
                    rfm.send(state.senderId, packet, PACKET_SIZE, /*requestACK=*/ false, /*sendACK=*/ true);
                    pinLed.stop();
                }

                USART_DEBUG((int)DataType::SENSOR_DATA
                            << ";" << state.senderId
                            << ";" << state.signalStrength
                            << ";" << state.data[0]
                            << ";" << reqId.word
                            << ";" << packet[3]
                            << ";" << packet[4]
                            << ".");
            }
        }
    }

};

#endif
#endif
