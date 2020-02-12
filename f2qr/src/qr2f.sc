import $ivy.`org.bytedeco:javacv-platform:latest.integration`
import $ivy.`org.bytedeco.javacpp-presets:ffmpeg:latest.integration`
import $ivy.`org.bytedeco.javacpp-presets:opencv:latest.integration`
import $ivy.`com.google.zxing:core:latest.integration`
import $ivy.`com.google.zxing:javase:latest.integration`
import $ivy.`org.bytedeco:javacv:latest.integration`
import os._
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BinaryBitmap
import com.google.zxing.qrcode.QRCodeReader
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.bytedeco.javacv.{FFmpegFrameGrabber, Java2DFrameConverter}

@main
def qr2f() = {
  def decode(raw: Array[Byte]) = java.nio.ByteBuffer.wrap(raw.take(4)).getInt -> raw.drop(4)

  val grabberFactory = new FFmpegFrameGrabber(s":0+0,0")
  grabberFactory.setFormat("x11grab")
  grabberFactory.setImageMode(ImageMode.GRAY)
  grabberFactory.setImageWidth(2560)
  grabberFactory.setImageHeight(1440)
  grabberFactory.setFrameRate(120)
  grabberFactory.start()

  val remain = scala.collection.mutable.Set[Int]()

  var done = false
  val grabber = Iterator.continually {
    grabberFactory.grabKeyFrame()
  }.takeWhile(_ => !done)

  val chunks = grabber.flatMap(frame => {
    val bitmap = new BinaryBitmap(
      new HybridBinarizer(
        new BufferedImageLuminanceSource(
          (new Java2DFrameConverter).convert(frame)
        )
      )
    )
    try {
      val qr = new QRCodeReader().decode(bitmap).getText
      println(qr)
      val wrap = decode(java.util.Base64.getDecoder.decode(qr))
      if (wrap._2.isEmpty) {
        remain ++= (1 to wrap._1).toSet
      }
      println(remain -= wrap._1)
      if (remain.isEmpty) {
        done = true
      }
      Some(wrap)
    } catch {
      case _: com.google.zxing.NotFoundException | _: com.google.zxing.FormatException | _: com.google.zxing.ChecksumException =>
        None
    }
  }).toSet.toArray.sortBy(_._1)
  val result: Array[Byte] = chunks.drop(1).flatMap(_._2)
  os.write.over(Path("qr2f.log", pwd), chunks.foldLeft("")((left, right)=>left + "\n" + s"${right._1} => ${java.util.Base64.getEncoder.encode(right._2)}"))
  os.write.over(Path("download", pwd), result)
}