# What HMG-API never does

- **It never sends its own messages on the car's network (CAN).** Every command goes
  through the head unit's own services, the same way the car's apps send them, and
  each command was reviewed before it was offered.
- **It never collects personal or per-car data.** No VIN, serial numbers, phone
  numbers, account details or certificates. The MICOM recorder (for developers)
  never records the telematics data and blanks any text in what it records.
- **It never flashes firmware:** not the MICOM, the bootloader or the modem.
- **It never changes the car's feature level** (`stepofspec`).
- **It never takes SOS or power away** from the car's own buttons.
- **It ships no manufacturer files.** Changes are made from your own head unit's
  stock files, and every file it changes can be put back byte for byte.
- **It doesn't update itself over the internet.** Updates come from a USB stick or
  adb, and only releases signed with HMG-API's key are accepted.
