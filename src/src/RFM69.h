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

#include "BasicIO.h"

#ifndef RFM69_h
#define RFM69_h

#define RF69_MAX_DATA_LEN       61 // to take advantage of the built in AES/CRC we want to limit the frame size to the internal FIFO size (66 bytes - 3 bytes overhead - 2 bytes crc)
#define CSMA_LIMIT              -50 // upper RX signal sensitivity threshold in dBm for carrier sense access
#define RF69_MODE_SLEEP         0 // XTAL OFF
#define RF69_MODE_STANDBY       1 // XTAL ON
#define RF69_MODE_SYNTH         2 // PLL ON
#define RF69_MODE_RX            3 // RX MODE
#define RF69_MODE_TX            4 // TX MODE

// available frequency bands
#define RF69_315MHZ             31 // non trivial values to avoid misconfiguration
#define RF69_433MHZ             43
#define RF69_868MHZ             86
#define RF69_915MHZ             91

#define COURSE_TEMP_COEF        -90 // puts the temperature reading in the ballpark, user can fine tune the returned value
#define RF69_BROADCAST_ADDR     255
#define RF69_CSMA_LIMIT_MS      1000
#define RF69_TX_LIMIT_MS        1000
#define RF69_FSTEP              61.03515625 // == FXOSC / 2^19 = 32MHz / 2^19 (p13 in datasheet)

// TWS: define CTLbyte bits
#define RFM69_CTL_SENDACK       0x80
#define RFM69_CTL_REQACK        0x40

class RFM69
{
public:

    class Payload
    {
    public:
        uint8_t size;
        uint8_t targetId, senderId, ctlByte;
        uint8_t data[RF69_MAX_DATA_LEN];
        int16_t signalStrength;

        Payload() :
            size{0},
            targetId{0},
            senderId{0},
            ctlByte{0},
            signalStrength{0}
        {
            ::memset(data, 0, RF69_MAX_DATA_LEN);
        }

        inline bool ackReceiver ()
        {
            return ctlByte & RFM69_CTL_SENDACK; // extract ACK-received flag
        }

        inline bool ackRequested ()
        {
            return ctlByte & RFM69_CTL_REQACK; // extract ACK-requested flag
        }
    };

    RFM69 (StmPlusPlus::Spi & _spi, const StmPlusPlus::IOPin & _csPort, const StmPlusPlus::IOPin _intPin);

    bool initialize (uint8_t freqBand, uint8_t ID, uint8_t networkID = 1);
    void setAddress (uint8_t addr);
    void setNetwork (uint8_t networkID);
    bool canSend ();
    uint32_t send (uint8_t toAddress, uint8_t* buffer, uint16_t bufferSize, bool requestACK = false, bool sendACK = false);

    /*
     * Return the frequency (in Hz)
     */
    uint32_t getFrequency ();
    void setFrequency (uint32_t freqHz);
    int16_t readRSSI (bool forceTrigger = false); // *current* signal strength indicator; e.g. < -90dBm says the frequency channel is free + ready to transmit
    void setHighPower (bool onOFF = true); // has to be called after initialize() for RFM69HW
    void setPowerLevel (uint8_t level); // reduce/increase transmit power level
    uint8_t readTemperature (uint8_t calFactor = 0); // get CMOS temperature (8bit)
    void rcCalibration (); // calibrate the internal RC oscillator for use in wide temperature variations - see datasheet section [4.3.5. RC Timer Accuracy]

    uint8_t readReg (uint8_t addr, bool _unselect = true);
    void writeReg (uint8_t addr, uint8_t val);
    void readAllRegs ();

    void receiveBegin ();
    bool readData (Payload & data);
    bool waitForResponce (StmPlusPlus::IOPin & pinDio0, Payload & data, uint32_t timeout);

    inline void setSpiDelay (uint32_t _spiDelay)
    {
        spiDelay = _spiDelay;
    }

protected:

    StmPlusPlus::Spi & spi;
    StmPlusPlus::IOPin slaveSelectPin;
    StmPlusPlus::IOPin interruptPin;
    uint8_t address;
    uint8_t powerLevel;
    uint32_t spiDelay;
    bool isRFM69HW;
    uint8_t mode;

    void setMode (uint8_t mode, bool waitForReady = false);
    void setHighPowerRegs (bool onOff);

    inline void select ()
    {
        slaveSelectPin.setLow();
    }

    inline void unselect ()
    {
        slaveSelectPin.setHigh();
    }

    uint8_t frame[256];
};

#endif
