[![License](https://img.shields.io/badge/license-GNU_GPLv3-orange.svg)](https://github.com/mkulesh/stm32WindowSensor/blob/master/LICENSE)

# stm32WindowSensor

*RF window sensors: STM32L + RFM69 + Android*

This is a DIY windows monitorig system that consist of:
- A window sensor based on STM32L051 MCU and RFM69 RF module. The PCB is developed in EagleCad, firmware written in C++ using System Workbench for STM32
- A server unit also based on STM32L051 MCU and RFM69 RF module
- A server software module (Java) that reads data from server unit and forwards it to the mobile app
- A mobile app for Android written in Java

## Window Sensor
<img src="https://github.com/mkulesh/stm32WindowSensor/blob/master/images/sensors/sensor01.jpg" align="center" height="800">

## Server Unit
<img src="https://github.com/mkulesh/stm32WindowSensor/blob/master/images/server/server03.jpg" align="center" height="800">

## Android App
<img src="https://github.com/mkulesh/stm32WindowSensor/blob/master/images/screenschots/floor_2_alarm.png" align="center" height="800">

## License

This software is published under the *GNU General Public License, Version 3*

Copyright © 2018-2019 by Mikhail Kulesh

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have
received a copy of the GNU General Public License along with this program.

If not, see [www.gnu.org/licenses](https://www.gnu.org/licenses).
