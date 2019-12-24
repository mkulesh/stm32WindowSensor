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

#define SENSOR_LOGIC true

#include "WindowSensor.h"
#include "SensorGateway.h"

__IO uint32_t sysTick;

SensorBase * appPtr = NULL;

int main (void)
{
    HAL_Init();

    IOPort defaultPortA(IOPort::PortName::A, GPIO_MODE_ANALOG, GPIO_NOPULL, GPIO_SPEED_LOW); __GPIOA_CLK_DISABLE();
    IOPort defaultPortB(IOPort::PortName::B, GPIO_MODE_ANALOG, GPIO_NOPULL, GPIO_SPEED_LOW); __GPIOB_CLK_DISABLE();
    IOPort defaultPortC(IOPort::PortName::C, GPIO_MODE_ANALOG, GPIO_NOPULL, GPIO_SPEED_LOW); __GPIOC_CLK_DISABLE();

#ifdef SENSOR_LOGIC
    WindowSensor::initSystemClock();
    WindowSensor::initWakeUp();
    WindowSensor sensor(/*sensorId=*/ 19);
    appPtr = &sensor;
    sensor.run();
#else
    SensorGateway::initSystemClock();
    SensorGateway::initWakeUp();
    SensorGateway gateway;
    appPtr = &gateway;
    gateway.run();
#endif

    while (true)
    {
        // empty
    }
}

extern "C" void EXTI0_1_IRQHandler (void)
{
    appPtr->processEXTI0_1_IRQn();
}

extern "C" void HAL_SYSTICK_Callback (void)
{
    ++sysTick;
}

