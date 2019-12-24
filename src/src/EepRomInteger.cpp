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

#include <EepRomInteger.h>
#include "stm32l0xx_hal_flash.h"

using namespace StmPlusPlus;


EepRomInteger::EepRomInteger(uint32_t _startAddr):
	startAddr(_startAddr)
{
	// empty
}


uint32_t EepRomInteger::readEEPROM (uint32_t address)
{
    address = DATA_EEPROM_BASE + address;
    return (*(__IO uint32_t*) address);
}


HAL_StatusTypeDef EepRomInteger::writeEEPROM (uint32_t address, uint8_t data)
 {
	HAL_StatusTypeDef status = HAL_ERROR;
    address = DATA_EEPROM_BASE + address;
	if (IS_FLASH_DATA_ADDRESS(address) && HAL_FLASHEx_DATAEEPROM_Unlock() == HAL_OK)
	{
		status = HAL_FLASHEx_DATAEEPROM_Program(FLASH_TYPEPROGRAMDATA_BYTE, address, data);
		HAL_FLASHEx_DATAEEPROM_Lock();
	}
    return status;
}


HAL_StatusTypeDef EepRomInteger::writeValue (uint32_t val)
{
	HAL_StatusTypeDef status = HAL_OK;
	LongToBytes longValue;
	longValue.value = val;
	if (writeEEPROM(startAddr + 0, longValue.bytes.b0) != HAL_OK)
	{
		status = HAL_ERROR;
	}
	if (writeEEPROM(startAddr + 1, longValue.bytes.b1))
	{
		status = HAL_ERROR;
	}
	if (writeEEPROM(startAddr + 2, longValue.bytes.b2))
	{
		status = HAL_ERROR;
	}
	if (writeEEPROM(startAddr + 3, longValue.bytes.b3))
	{
		status = HAL_ERROR;
	}
	return status;
}
