import $ivy.`org.bytedeco:javacv-platform:latest.integration`
import $ivy.`org.bytedeco.javacpp-presets:ffmpeg:latest.integration`
import $ivy.`org.bytedeco.javacpp-presets:opencv:latest.integration`
import $ivy.`com.google.zxing:core:latest.integration`
import $ivy.`com.google.zxing:javase:latest.integration`
import $ivy.`org.bytedeco:javacv:latest.integration`
import os._
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.{BinaryBitmap, DecodeHintType}
import com.google.zxing.qrcode.QRCodeReader
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.bytedeco.javacv.{FFmpegFrameGrabber, Java2DFrameConverter}
import scala.jdk.CollectionConverters._

@main
def qr2f() = {
  def decode(raw: Array[Byte]) = java.nio.ByteBuffer.wrap(raw.take(4)).getInt -> raw.drop(4)

  val screen = java.awt.Toolkit.getDefaultToolkit.getScreenSize
  val grabberFactory = new FFmpegFrameGrabber(s"${System.getenv("DISPLAY")}+0,0")
  grabberFactory.setFormat("x11grab")
  grabberFactory.setImageMode(ImageMode.GRAY)
  grabberFactory.setImageWidth(screen.getWidth.toInt)
  grabberFactory.setImageHeight(screen.getHeight.toInt)
  grabberFactory.start()

  val remain = scala.collection.mutable.Set[Int]()

  var start = false
  var done = false
  val grabber = Iterator.continually {
    grabberFactory.grabKeyFrame()
  }.takeWhile(_ => !done)

  os.write.over(Path("qr2f.log", pwd), "")
  val chunks = grabber.flatMap(frame => {
    val bitmap = new BinaryBitmap(
      new HybridBinarizer(
        new BufferedImageLuminanceSource(
          (new Java2DFrameConverter).convert(frame)
        )
      )
    )
    try {
      val qr = new QRCodeReader().decode(bitmap, Map(
        DecodeHintType.CHARACTER_SET -> "ISO-8859-1",
        DecodeHintType.TRY_HARDER -> true
      ).asJava).getText
      val wrap = decode(qr.getBytes("ISO-8859-1"))
      os.write.append(Path("qr2f.log", pwd), s"${wrap._1} -> ${qr.getBytes("ISO-8859-1").map("%02x" format _).mkString}\n")
      if (wrap._2.isEmpty) {
        remain ++= (1 to wrap._1).toSet
        start = true
        None
      } else {
        remain -= wrap._1
        println("\033c")
        Some(wrap)
      }
    } catch {
      case _: com.google.zxing.NotFoundException | _: com.google.zxing.FormatException | _: com.google.zxing.ChecksumException =>
        println(remain.foldLeft("")((left, right) => left + "," + right.toString))
        None
    } finally {
      if (start) {
        if (remain.isEmpty) {
          done = true
        }
      }
    }
  }).toMap.toArray.sortBy(_._1)
  val result: Array[Byte] = chunks.flatMap(_._2)
  os.write.over(Path("download", pwd), result)
}
