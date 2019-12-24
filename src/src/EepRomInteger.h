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

#ifndef REQUEST_ID_H_
#define REQUEST_ID_H_

#include "stm32l0xx_hal.h"

namespace StmPlusPlus {

typedef union
{
	uint32_t value;
	struct
	{
		uint8_t b0;
		uint8_t b1;
		uint8_t b2;
		uint8_t b3;
	} bytes;
} LongToBytes;


/**
 * @brief Class implementing persistent request ID stored in EEPROM.
 */
class EepRomInteger
{
public:

	/**
	 * @brief Default constructor.
	 */
	EepRomInteger (uint32_t _startAddr);

	inline uint32_t readValue ()
	{
		return readEEPROM(startAddr);
	};

	HAL_StatusTypeDef writeValue (uint32_t val);

private:

	const uint32_t startAddr;
	uint32_t readEEPROM (uint32_t address);
	HAL_StatusTypeDef writeEEPROM (uint32_t address, uint8_t data);

};

} // end of namespace StmPlusPlus

#endif
