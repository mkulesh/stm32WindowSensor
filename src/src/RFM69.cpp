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

#include <algorithm>
#include "RFM69.h"
#include "RFM69registers.h"

using namespace StmPlusPlus;
#define USART_DEBUG_MODULE "RFM: "


RFM69::RFM69 (StmPlusPlus::Spi & _spi, const StmPlusPlus::IOPin & _csPort, const StmPlusPlus::IOPin _intPin) :
    spi { _spi },
    slaveSelectPin { _csPort },
    interruptPin { _intPin },
    address { 0 },
    powerLevel { 31 },
    spiDelay { 2 },
    isRFM69HW { false },
    mode { RF69_MODE_SLEEP }
{
    // empty
}


bool RFM69::initialize (uint8_t freqBand, uint8_t nodeID, uint8_t networkID)
{
    const uint8_t CONFIG[][2] = {
        /* 0x01 */{ REG_OPMODE, RF_OPMODE_SEQUENCER_ON | RF_OPMODE_LISTEN_OFF | RF_OPMODE_STANDBY },
        /* 0x02 */{ REG_DATAMODUL, RF_DATAMODUL_DATAMODE_PACKET | RF_DATAMODUL_MODULATIONTYPE_FSK | RF_DATAMODUL_MODULATIONSHAPING_00 }, // no shaping
        /* 0x03 */{ REG_BITRATEMSB, RF_BITRATEMSB_55555 }, // default: 4.8 KBPS
        /* 0x04 */{ REG_BITRATELSB, RF_BITRATELSB_55555 },
        /* 0x05 */{ REG_FDEVMSB, RF_FDEVMSB_50000 }, // default: 5KHz, (FDEV + BitRate / 2 <= 500KHz)
        /* 0x06 */{ REG_FDEVLSB, RF_FDEVLSB_50000 },

        /* 0x07 */{ REG_FRFMSB,
                    (uint8_t) (
                        freqBand == RF69_315MHZ ?
                            RF_FRFMSB_315 :
                            (freqBand == RF69_433MHZ ?
                                RF_FRFMSB_433 :
                                (freqBand == RF69_868MHZ ? RF_FRFMSB_868 : RF_FRFMSB_915))) },
        /* 0x08 */{ REG_FRFMID,
                    (uint8_t) (
                        freqBand == RF69_315MHZ ?
                            RF_FRFMID_315 :
                            (freqBand == RF69_433MHZ ?
                                RF_FRFMID_433 :
                                (freqBand == RF69_868MHZ ? RF_FRFMID_868 : RF_FRFMID_915))) },
        /* 0x09 */{ REG_FRFLSB,
                    (uint8_t) (
                        freqBand == RF69_315MHZ ?
                            RF_FRFLSB_315 :
                            (freqBand == RF69_433MHZ ?
                                RF_FRFLSB_433 :
                                (freqBand == RF69_868MHZ ? RF_FRFLSB_868 : RF_FRFLSB_915))) },

        // looks like PA1 and PA2 are not implemented on RFM69W, hence the max output power is 13dBm
        // +17dBm and +20dBm are possible on RFM69HW
        // +13dBm formula: Pout = -18 + OutputPower (with PA0 or PA1**)
        // +17dBm formula: Pout = -14 + OutputPower (with PA1 and PA2)**
        // +20dBm formula: Pout = -11 + OutputPower (with PA1 and PA2)** and high power PA settings (section 3.3.7 in datasheet)
        ///* 0x11 */ { REG_PALEVEL, RF_PALEVEL_PA0_ON | RF_PALEVEL_PA1_OFF | RF_PALEVEL_PA2_OFF | RF_PALEVEL_OUTPUTPOWER_11111},
        ///* 0x13 */ { REG_OCP, RF_OCP_ON | RF_OCP_TRIM_95 }, // over current protection (default is 95mA)

        // RXBW defaults are { REG_RXBW, RF_RXBW_DCCFREQ_010 | RF_RXBW_MANT_24 | RF_RXBW_EXP_5} (RxBw: 10.4KHz)
        /* 0x19 */{ REG_RXBW, RF_RXBW_DCCFREQ_010 | RF_RXBW_MANT_16 | RF_RXBW_EXP_2 }, // (BitRate < 2 * RxBw)
        //for BR-19200: /* 0x19 */ { REG_RXBW, RF_RXBW_DCCFREQ_010 | RF_RXBW_MANT_24 | RF_RXBW_EXP_3 },
        /* 0x25 */{ REG_DIOMAPPING1, RF_DIOMAPPING1_DIO0_01 }, // DIO0 is the only IRQ we're using
        /* 0x26 */{ REG_DIOMAPPING2, RF_DIOMAPPING2_CLKOUT_OFF }, // DIO5 ClkOut disable for power saving
        /* 0x28 */{ REG_IRQFLAGS2, RF_IRQFLAGS2_FIFOOVERRUN }, // writing to this bit ensures that the FIFO & status flags are reset
        /* 0x29 */{ REG_RSSITHRESH, 220 }, // must be set to dBm = (-Sensitivity / 2), default is 0xE4 = 228 so -114dBm
        ///* 0x2D */ { REG_PREAMBLELSB, RF_PREAMBLESIZE_LSB_VALUE } // default 3 preamble bytes 0xAAAAAA
        /* 0x2E */{ REG_SYNCCONFIG, RF_SYNC_ON | RF_SYNC_FIFOFILL_AUTO | RF_SYNC_SIZE_2 | RF_SYNC_TOL_0 },
        /* 0x2F */{ REG_SYNCVALUE1, 0x2D }, // attempt to make this compatible with sync1 byte of RFM12B lib
        /* 0x30 */{ REG_SYNCVALUE2, networkID }, // NETWORK ID
        /* 0x37 */{ REG_PACKETCONFIG1, RF_PACKET1_FORMAT_VARIABLE | RF_PACKET1_DCFREE_OFF | RF_PACKET1_CRC_ON | RF_PACKET1_CRCAUTOCLEAR_ON | RF_PACKET1_ADRSFILTERING_OFF },
        /* 0x38 */{ REG_PAYLOADLENGTH, RF69_MAX_DATA_LEN + 5 }, // in variable length mode: the max frame size, not used in TX
        ///* 0x39 */ { REG_NODEADRS, nodeID }, // turned off because we're not using address filtering
        /* 0x3C */{ REG_FIFOTHRESH, RF_FIFOTHRESH_TXSTART_FIFONOTEMPTY | RF_FIFOTHRESH_VALUE }, // TX on FIFO not empty
        /* 0x3D */{ REG_PACKETCONFIG2, RF_PACKET2_RXRESTARTDELAY_2BITS | RF_PACKET2_AUTORXRESTART_ON | RF_PACKET2_AES_OFF }, // RXRESTARTDELAY must match transmitter PA ramp-down time (bitrate dependent)
        //for BR-19200: /* 0x3D */ { REG_PACKETCONFIG2, RF_PACKET2_RXRESTARTDELAY_NONE | RF_PACKET2_AUTORXRESTART_ON | RF_PACKET2_AES_OFF }, // RXRESTARTDELAY must match transmitter PA ramp-down time (bitrate dependent)
        /* 0x6F */{ REG_TESTDAGC, RF_DAGC_IMPROVED_LOWBETA0 }, // run DAGC continuously in RX mode for Fading Margin Improvement, recommended default for AfcLowBetaOn=0
        { 255, 0 } };

    uint32_t start = HAL_GetTick();
    uint32_t timeout = 50;
    do
    {
        writeReg(REG_SYNCVALUE1, 0xAA);
    }
    while (readReg(REG_SYNCVALUE1) != 0xaa && HAL_GetTick() - start < timeout);
    start = HAL_GetTick();
    do
    {
        writeReg(REG_SYNCVALUE1, 0x55);
    }
    while (readReg(REG_SYNCVALUE1) != 0x55 && HAL_GetTick() - start < timeout);

    for (uint8_t i = 0; CONFIG[i][0] != 255; i++)
    {
        writeReg(CONFIG[i][0], CONFIG[i][1]);
    }

    setMode(RF69_MODE_STANDBY);
    start = HAL_GetTick();
    while (((readReg(REG_IRQFLAGS1) & RF_IRQFLAGS1_MODEREADY) == 0x00) && HAL_GetTick() - start < timeout); // wait for ModeReady
    if (HAL_GetTick() - start >= timeout)
    {
        return false;
    }

    setAddress(nodeID);
    return true;
}


uint32_t RFM69::getFrequency ()
{
    return RF69_FSTEP * (((uint32_t) readReg(REG_FRFMSB) << 16) + ((uint16_t) readReg(REG_FRFMID) << 8) + readReg(REG_FRFLSB));
}


// set the frequency (in Hz)
void RFM69::setFrequency (uint32_t freqHz)
{
    uint8_t oldMode = mode;
    if (oldMode == RF69_MODE_TX)
    {
        setMode(RF69_MODE_RX);
    }
    freqHz /= RF69_FSTEP; // divide down by FSTEP to get FRF
    writeReg(REG_FRFMSB, freqHz >> 16);
    writeReg(REG_FRFMID, freqHz >> 8);
    writeReg(REG_FRFLSB, freqHz);
    if (oldMode == RF69_MODE_RX)
    {
        setMode(RF69_MODE_SYNTH);
    }
    setMode(oldMode);
}


void RFM69::setMode (uint8_t newMode, bool waitForReady)
{
    if (newMode == mode) return;

    switch (newMode)
    {
    case RF69_MODE_TX:
        writeReg(REG_OPMODE, (readReg(REG_OPMODE) & 0xE3) | RF_OPMODE_TRANSMITTER);
        if (isRFM69HW) setHighPowerRegs(true);
        break;
    case RF69_MODE_RX:
        writeReg(REG_OPMODE, (readReg(REG_OPMODE) & 0xE3) | RF_OPMODE_RECEIVER);
        if (isRFM69HW) setHighPowerRegs(false);
        break;
    case RF69_MODE_SYNTH:
        writeReg(REG_OPMODE, (readReg(REG_OPMODE) & 0xE3) | RF_OPMODE_SYNTHESIZER);
        break;
    case RF69_MODE_STANDBY:
        writeReg(REG_OPMODE, (readReg(REG_OPMODE) & 0xE3) | RF_OPMODE_STANDBY);
        break;
    case RF69_MODE_SLEEP:
        writeReg(REG_OPMODE, (readReg(REG_OPMODE) & 0xE3) | RF_OPMODE_SLEEP);
        break;
    default:
        return;
    }

    if (waitForReady)
    {
        while ((readReg(REG_IRQFLAGS1) & RF_IRQFLAGS1_MODEREADY) == 0x00); // wait for ModeReady
    }

    mode = newMode;
}


//set this node's address
void RFM69::setAddress (uint8_t addr)
{
    address = addr;
    writeReg(REG_NODEADRS, address);
}


//set this node's network id
void RFM69::setNetwork (uint8_t networkID)
{
    writeReg(REG_SYNCVALUE2, networkID);
}


// set *transmit/TX* output power: 0=min, 31=max
// this results in a "weaker" transmitted signal, and directly results in a lower RSSI at the receiver
// the power configurations are explained in the SX1231H datasheet (Table 10 on p21; RegPaLevel p66): http://www.semtech.com/images/datasheet/sx1231h.pdf
// valid powerLevel parameter values are 0-31 and result in a directly proportional effect on the output/transmission power
// this function implements 2 modes as follows:
//       - for RFM69W the range is from 0-31 [-18dBm to 13dBm] (PA0 only on RFIO pin)
//       - for RFM69HW the range is from 0-31 [5dBm to 20dBm]  (PA1 & PA2 on PA_BOOST pin & high Power PA settings - see section 3.3.7 in datasheet, p22)
void RFM69::setPowerLevel (uint8_t _powerLevel)
{
    powerLevel = (_powerLevel > 31 ? 31 : _powerLevel);
    if (isRFM69HW) powerLevel /= 2;
    writeReg(REG_PALEVEL, (readReg(REG_PALEVEL) & 0xE0) | powerLevel);
}

uint32_t RFM69::send (uint8_t toAddress, uint8_t* buffer, uint16_t bufferSize, bool requestACK, bool sendACK)
{
    setMode(RF69_MODE_STANDBY, /*waitForReady=*/ true); // turn off receiver to prevent reception while filling fifo
    writeReg(REG_DIOMAPPING1, RF_DIOMAPPING1_DIO0_00); // DIO0 is "Packet Sent"

    // control byte
    if (bufferSize > RF69_MAX_DATA_LEN)
    {
        bufferSize = RF69_MAX_DATA_LEN;
    }

    uint8_t CTLbyte = 0x00;
    if (sendACK)
    {
        CTLbyte = RFM69_CTL_SENDACK;
    }
    else if (requestACK)
    {
        CTLbyte = RFM69_CTL_REQACK;
    }

    uint8_t cmd[6] = {(uint8_t)(REG_FIFO | 0x80), (uint8_t)(bufferSize + 3), toAddress, address, CTLbyte, 0};

    // write to FIFO
    select();
    spi.transmitBlocking(cmd, 5);
    spi.transmitBlocking(buffer, bufferSize);
    unselect();

    // no need to wait for transmit mode to be ready since its handled by the radio
    setMode(RF69_MODE_TX);

    uint32_t txStart = HAL_GetTick();
    while (!interruptPin.getBit() && HAL_GetTick() - txStart < RF69_TX_LIMIT_MS); // wait for DIO0 to turn HIGH signalling transmission finish
    setMode(RF69_MODE_STANDBY, /*waitForReady=*/ true);
    receiveBegin();

    return (HAL_GetTick() - txStart);
}


// internal function - interrupt gets called when a packet is received
bool RFM69::readData (RFM69::Payload & data)
{
    if (mode == RF69_MODE_RX && (readReg(REG_IRQFLAGS2) & RF_IRQFLAGS2_PAYLOADREADY))
    {
        // clear output frame
        data.targetId = data.senderId = data.ctlByte = 0xFF;
        data.signalStrength = readRSSI();
        ::memset(data.data, 0, RF69_MAX_DATA_LEN);

        // read frame
        setMode(RF69_MODE_STANDBY, /*waitForReady=*/ true);
        data.size = readReg(REG_FIFO, false);
        HAL_StatusTypeDef errorCode = spi.receiveBlocking(frame, data.size, 100);
        unselect();
        setMode(RF69_MODE_RX);

        // parse frame
        if (errorCode == HAL_OK)
        {
            data.targetId = frame[0];
            data.senderId = frame[1];
            data.ctlByte = frame[2];
            for (int i = 3; i < data.size; i++)
            {
                data.data[i - 3] = frame[i];
            }
            return true;
        }
    }
    return false;
}


bool RFM69::waitForResponce (StmPlusPlus::IOPin & pinDio0, RFM69::Payload & data, uint32_t timeout)
{
    uint32_t start = HAL_GetTick();
    while (timeout == __UINT32_MAX__ || HAL_GetTick() - start < timeout)
    {
        if (!pinDio0.getBit())
        {
            continue;
        }

        if (readData(data))
        {
            return true;
        }
    }
    return false;
}


// internal function
void RFM69::receiveBegin ()
{
    if (readReg(REG_IRQFLAGS2) & RF_IRQFLAGS2_PAYLOADREADY)
    {
        writeReg(REG_PACKETCONFIG2, (readReg(REG_PACKETCONFIG2) & 0xFB) | RF_PACKET2_RXRESTART); // avoid RX deadlocks
    }
    writeReg(REG_DIOMAPPING1, RF_DIOMAPPING1_DIO0_01); // set DIO0 to "PAYLOADREADY" in receive mode
    setMode(RF69_MODE_RX);
}


// get the received signal strength indicator (RSSI)
int16_t RFM69::readRSSI (bool forceTrigger)
{
    int16_t rssi = 0;
    if (forceTrigger)
    {
        // RSSI trigger not needed if DAGC is in continuous mode
        writeReg(REG_RSSICONFIG, RF_RSSI_START);
        while ((readReg(REG_RSSICONFIG) & RF_RSSI_DONE) == 0x00); // wait for RSSI_Ready
    }
    rssi = -readReg(REG_RSSIVALUE);
    rssi >>= 1;
    return rssi;
}


uint8_t RFM69::readReg (uint8_t addr, bool _unselect)
{
    // send address + r/w bit
    uint8_t out[2] = {(uint8_t)(addr & 0x7F), 0};
    uint8_t in[2] = {0,0};
    select();
    spi.transmitBlocking(out, 1);
    HAL_Delay(spiDelay);
    spi.receiveBlocking(in, 1);
    if (_unselect)
    {
        unselect();
    }
    return in[0];
}


void RFM69::writeReg (uint8_t addr, uint8_t value)
{
    uint8_t out[3] = {(uint8_t)(addr | 0x80), value, 0};
    select();
    spi.transmitBlocking(out, 2);
    unselect();
}


// for RFM69HW only: you must call setHighPower(true) after initialize() or else transmission won't work
void RFM69::setHighPower (bool onOff)
{
    isRFM69HW = onOff;
    writeReg(REG_OCP, isRFM69HW ? RF_OCP_OFF : RF_OCP_ON);
    if (isRFM69HW) // turning ON
    {
        writeReg(REG_PALEVEL,
                 (readReg(REG_PALEVEL) & 0x1F) | RF_PALEVEL_PA1_ON | RF_PALEVEL_PA2_ON); // enable P1 & P2 amplifier stages
    }
    else
    {
        writeReg(REG_PALEVEL,
                 RF_PALEVEL_PA0_ON | RF_PALEVEL_PA1_OFF | RF_PALEVEL_PA2_OFF | powerLevel); // enable P0 only
    }
}


void RFM69::setHighPowerRegs (bool onOff)
{
    writeReg(REG_TESTPA1, onOff ? 0x5D : 0x55);
    writeReg(REG_TESTPA2, onOff ? 0x7C : 0x70);
}


void RFM69::readAllRegs ()
{
    for (uint8_t regAddr = 1; regAddr <= 0x4F; regAddr++)
    {
        uint8_t regVal = readReg(regAddr);
        if (regVal != 0)
        {
            USART_DEBUG(UsartLogger::Manupulator::TAB << UsartLogger::Manupulator::HEX << regAddr << ": " << regVal);
        }
    }
}


uint8_t RFM69::readTemperature (uint8_t calFactor) // returns centigrade
{
    setMode(RF69_MODE_STANDBY, /*waitForReady=*/ true);
    writeReg(REG_TEMP1, RF_TEMP1_MEAS_START);
    while ((readReg(REG_TEMP1) & RF_TEMP1_MEAS_RUNNING));
    return ~readReg(REG_TEMP2) + COURSE_TEMP_COEF + calFactor; // 'complement' corrects the slope, rising temp = rising val
}


void RFM69::rcCalibration ()
{
    writeReg(REG_OSC1, RF_OSC1_RCCAL_START);
    while ((readReg(REG_OSC1) & RF_OSC1_RCCAL_DONE) == 0x00);
}

