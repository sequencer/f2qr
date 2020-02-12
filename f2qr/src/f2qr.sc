import $ivy.`com.google.zxing:core:latest.integration`
import $ivy.`com.google.zxing:javase:latest.integration`
import $ivy.`org.jline:jline:latest.integration`
import os._
import com.google.zxing._
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

import scala.jdk.CollectionConverters._

def fileSize(consoleSize: Int): Int = {
  println(s"detecting max framesize from $consoleSize")
  var tmp = 768
  var bytes = new Array[Byte](tmp)
  scala.util.Random.nextBytes(bytes)
  var qr = toQR(bytes, consoleSize, ErrorCorrectionLevel.H)
  while (qr.getWidth > consoleSize && qr.getHeight > consoleSize) {
    tmp -= 1
    bytes = new Array[Byte](tmp)
    scala.util.Random.nextBytes(bytes)
    qr = toQR(bytes, consoleSize, ErrorCorrectionLevel.H)
  }
  // contains index header
  tmp - 4
}

def readBinary(f: ReadablePath, consoleSize: Int) = {
  val raw = os.read.bytes(f).grouped(fileSize(consoleSize)).toIndexedSeq
  val buf = java.nio.ByteBuffer.allocate(4)
  val head: Array[Byte] = {
    val lengthByte = buf.putInt(raw.length).array
    buf.clear()
    lengthByte
  }
  Seq(head) ++ raw.zipWithIndex.map { b =>
    // start from 1
    val sizeArray = buf.putInt(b._2 + 1).array
    buf.clear()
    sizeArray ++ b._1
  }
}

def toQR(data: Array[Byte], consoleSize: Int, ec: ErrorCorrectionLevel): BitMatrix = {
  val str = new String(java.util.Base64.getEncoder.encode(data))
  new MultiFormatWriter().encode(str, BarcodeFormat.QR_CODE, consoleSize, consoleSize,
    Map(
      EncodeHintType.MARGIN -> 2,
      EncodeHintType.ERROR_CORRECTION -> ec,
      EncodeHintType.CHARACTER_SET -> java.nio.charset.StandardCharsets.US_ASCII,
    ).asJava
  )
}

def qrString(bitMatrix: BitMatrix): String = {
  val sb = new StringBuilder()
  val height = bitMatrix.getHeight
  val weight = bitMatrix.getWidth
  (0 until height).map { row =>
    (0 until weight).map { col =>
      sb.append(
        if (bitMatrix.get(row, col))
          "\033[40m  \033[0m"
        else
          "\033[47m  \033[0m"
      )
    }
    sb.append("\n")
  }
  sb.toString
}

@main
def f2qr(path: os.Path, framerate: Int, imageSize: Int = 0, section: Seq[Int] = Seq(), ecLevel: Int = 1, debug: Boolean = false): Unit = {
  val consoleSize = if (imageSize == 0) {
    val console = org.jline.terminal.TerminalBuilder.terminal()
    console.getWidth min console.getHeight - 4
  } else {
    imageSize
  }
  val file = readBinary(path, consoleSize)
  if (debug) {
    os.write.over(Path("f2qr.log", pwd), file.zipWithIndex.foldLeft("")((left, right)=>left + "\n" + s"${right._2} => ${java.util.Base64.getEncoder.encode(right._1)}"))
    return
  }
  val toPrint = if (section.isEmpty)
    file.zipWithIndex
  else {
    file.zipWithIndex.filter(s => section.contains(s._2)).map(_._1).zipWithIndex
  }
  toPrint.foreach(d => {
    println(s"\033c${d._2} of ${toPrint.length}")
    print(qrString(toQR(d._1, consoleSize, ecLevel match {
      case 1 => ErrorCorrectionLevel.L
      case 2 => ErrorCorrectionLevel.M
      case 3 => ErrorCorrectionLevel.Q
      case 4 => ErrorCorrectionLevel.H
    })))
    Thread.sleep(1000 / framerate)
  })
  println(s"console: $consoleSize")
}