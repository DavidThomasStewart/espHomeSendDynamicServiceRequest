library(
    name: 'espHomeSendDynamicServiceRequest',
    namespace: 'esphome',
    author: 'david@lightcoresystems.com',
    description: 'ESPHome Dynamic Protobuf API Extension Library'
)

public static String wireTypeString(int type) 
{
    def Map<Integer,String> WIRETYPE_NAMES = [
        (WIRETYPE_VARINT)          : "VARINT",
        (WIRETYPE_FIXED64)         : "FIXED64",
        (WIRETYPE_LENGTH_DELIMITED): "LENGTH_DELIMITED",
        (WIRETYPE_FIXED32)         : "FIXED32"
    ]
    return WIRETYPE_NAMES.getOrDefault(type, "UNKNOWN")
}

public void espHomeSendDynamicServiceRequest(Long serviceKey, List<Map> argsList) 
{
    ByteArrayOutputStream payload = new ByteArrayOutputStream()

    // --- Field #1: service_key (fixed32) ---
    int tag1 = (1 << 3) | WIRETYPE_FIXED32
    writeVarInt(payload, tag1)
    int v32 = serviceKey
    (0..<4).each { payload.write(v32 & 0xFF); v32 >>= 8 }

    int pNum = 1;

    // --- Field #2: repeated args ---
    argsList.each { arg ->
      // Outer tag for this arg submessage
      int tag2 = (2 << 3) | WIRETYPE_LENGTH_DELIMITED
      ByteArrayOutputStream argBuf = new ByteArrayOutputStream()

      // Each arg submessage: field #2 = value
      int innerTag = (2 << 3) | arg.type
      writeVarInt(argBuf, innerTag)

      switch (arg.type) 
      {
          case WIRETYPE_VARINT:
              writeVarInt(argBuf, arg.value as Integer)
              break

          case WIRETYPE_FIXED32:
              int v32a = arg.value as int
              (0..<4).each { argBuf.write(v32a & 0xFF); v32a >>= 8 }
              break

          case WIRETYPE_FIXED64:
              long v64 = arg.value as long
              (0..<8).each { argBuf.write((int)(v64 & 0xFF)); v64 >>= 8 }
              break

          case WIRETYPE_LENGTH_DELIMITED:
              byte[] bytes = (arg.value instanceof String) ?
                  (arg.value as String).getBytes("UTF-8") :
                  (arg.value as byte[])

              writeVarInt(argBuf, bytes.size())
              argBuf.write(bytes)
              break
      }

      // Emit outer tag + length + submessage
      writeVarInt(payload, tag2)
      byte[] argBytes = argBuf.toByteArray()
      writeVarInt(payload, argBytes.size())
      payload.write(argBytes)

      log.debug "[${pNum}]: ['${arg.id}', ${wireTypeString(arg.type)}] = ${arg.value}"

      pNum++;
    }

    // --- Wrap with frame ---
    ByteArrayOutputStream stream = new ByteArrayOutputStream()
    stream.write(0x00) // start byte
    writeVarInt(stream, payload.size())
    writeVarInt(stream, MSG_EXECUTE_SERVICE_REQUEST)
    payload.writeTo(stream)

    String hexStr = HexUtils.byteArrayToHexString(stream.toByteArray())
    log.debug "HexStream[${hexStr}]"
    
    interfaces.rawSocket.sendMessage(hexStr)
}
