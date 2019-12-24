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

#ifndef WINDOW_SENSOR_H_
#define WINDOW_SENSOR_H_

#ifdef SENSOR_LOGIC

#include "SensorBase.h"
#include "EepRomInteger.h"

using namespace StmPlusPlus;

#define USART_DEBUG_MODULE "WS;"

class WindowSensor : public SensorBase
{
private:
    
    // Sensor ID
    uint8_t sensorId;

    // Magnetic switch
    IOPin pinSwithValue;

    // Last reported data sensor state
    EepRomInteger lastReportedRequestId, lastReportedSensorState;

    // Data packet
    uint8_t packet[PACKET_SIZE];
    RFM69::Payload response;

    // Logging
    UsartLogger log;
    uint32_t startTime;

public:
    
    WindowSensor (uint8_t _sensorId) :
        SensorBase(),

        // Sensor ID
        sensorId(_sensorId),

        // Magnetic switch
        pinSwithValue(IOPort::A, PIN_SWITH_VALUE, GPIO_MODE_IT_RISING_FALLING, GPIO_PULLDOWN, GPIO_SPEED_FREQ_LOW),

        // Last reported sensor state
        lastReportedRequestId(0),
        lastReportedSensorState(4),

        // Logging
        log(Usart::USART_1, IOPort::A, GPIO_PIN_9, GPIO_PIN_10, GPIO_SPEED_FREQ_LOW, 19200),
        startTime(HAL_GetTick())
    {
        // empty
    }
    
    static void initSystemClock (void)
    {
        RCC_OscInitTypeDef RCC_OscInitStruct;
        RCC_ClkInitTypeDef RCC_ClkInitStruct;
        
        __HAL_RCC_PWR_CLK_ENABLE();
        __HAL_PWR_VOLTAGESCALING_CONFIG(PWR_REGULATOR_VOLTAGE_SCALE3);
        
        RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_MSI;
        RCC_OscInitStruct.MSIState = RCC_MSI_ON;
        RCC_OscInitStruct.MSICalibrationValue = 0;
        RCC_OscInitStruct.MSIClockRange = RCC_MSIRANGE_4;
        RCC_OscInitStruct.PLL.PLLState = RCC_PLL_NONE;
        HAL_RCC_OscConfig(&RCC_OscInitStruct);
        
        RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                                    |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
        RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_MSI;
        RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
        RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV1;
        RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;
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
        //log.initInstance();

        uint8_t vdd = getMedianVoltage();
        uint32_t pwm = getLedPwm(vdd);

        //USART_DEBUG((int)DataType::STARTUP << ";" << SystemCoreClock << ";" << vdd << ";" << (HAL_GetTick() - startTime) << "ms.");
        if (vdd < MIN_VOLTAGE)
        {
            USART_DEBUG((int)DataType::ERROR << ";" << sensorId << ";Low voltage");
            enterStandBy(false);
        }

        rfm.setSpiDelay(1);
        if (!initRfm(sensorId, pwm))
        {
            USART_DEBUG((int)DataType::ERROR << ";" << sensorId << ";Can not initialize RFM.");
            enterStandBy();
        }

        waitForTransmit();
        //USART_DEBUG((int)DataType::WAIT_FOR_READY << ";" << sensorId << ";" << (HAL_GetTick() - startTime) << "ms.");

        reportSensor(vdd, pwm);

        //USART_DEBUG((int)DataType::STANDBY << ";" << sensorId << ";" << (HAL_GetTick() - startTime) << "ms.");
        //HAL_Delay(20);
        
        enterStandBy();
    }
    
    void waitForTransmit()
    {
        uint32_t delay = 5 * sensorId;
        rfm.receiveBegin();
        uint32_t receiveTime = HAL_GetTick();
        do
        {
            int16_t rssi = rfm.readRSSI(false);
            if (rssi > CSMA_LIMIT)
            {
                receiveTime = HAL_GetTick();
            }
            if (pinRfmDio0.getBit() && rfm.readData(response))
            {
                receiveTime = HAL_GetTick();
            }
        }
        while(HAL_GetTick() - receiveTime <= delay);
    }

    void reportSensor (uint8_t vdd, uint32_t pwm)
    {
        uint32_t reqId = lastReportedRequestId.readValue();
        while (switchChanges > 0)
        {
            reqId++;
            bool reportedValue = pinSwithValue.getBit();
            switchChanges = 0;
            if (sendSensorState(reqId, reportedValue, vdd, pwm))
            {
                if (lastReportedRequestId.writeValue(reqId) != HAL_OK)
                {
                    USART_DEBUG((int)DataType::ERROR << ";" << sensorId << ";Can not store request ID in EEPROM");
                }
            }
        }
    }

    bool sendSensorState (uint16_t reqId, uint8_t sensorState, uint8_t vdd, uint32_t pwm)
    {
        bool retValue = false;

        WordToBytes w;
        w.word = reqId;

        packet[0] = sensorState;
        packet[1] = w.bytes.low;
        packet[2] = w.bytes.high;
        packet[3] = vdd;
        packet[4] = rfm.readTemperature(0);
        packet[5] = CRC7_buf(packet, PACKET_SIZE - 2);
        packet[6] = 0xFF;

        uint16_t respId = 0;
        for (size_t i = 0; i < REPEAT_ON_ERROR_COUNT; i++)
        {
            pinLed.start(100, pwm);
            rfm.send(BASE_ID, packet, PACKET_SIZE, /*requestACK=*/ true);
            pinLed.stop();
            if (rfm.waitForResponce(pinRfmDio0, response, 100))
            {
                w.bytes.low = response.data[1];
                w.bytes.high = response.data[2];
                respId = w.word;
                if (response.senderId == BASE_ID &&
                    response.targetId == sensorId &&
                    response.ackReceiver() &&
                    respId == reqId)
                {
                    retValue = true;
                    break;
                }
            }
        }

        USART_DEBUG((int)DataType::SENSOR_DATA
                    << ";" << sensorId
                    << ";" << response.signalStrength
                    << ";" << packet[0]
                    << ";" << reqId
                    << ";" << packet[3]
                    << ";" << packet[4]
                    << ";" << (HAL_GetTick() - startTime) << "ms.");

        return retValue;
    }

};

#endif
#endif
