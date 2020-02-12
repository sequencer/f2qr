import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalalib.scalafmt._

object f2qr extends ScalaModule {
  def scalaVersion = "2.12.10"
  override def ivyDeps = Agg(
    ivy"com.lihaoyi::os-lib:latest.integration",
    ivy"com.lihaoyi::upickle:latest.integration",
    ivy"com.lihaoyi::ammonite-ops:latest.integration",
    ivy"org.bytedeco:javacv-platform:latest.integration",
    ivy"com.google.zxing:core:latest.integration",
    ivy"com.google.zxing:javase:latest.integration",
    ivy"org.bytedeco:javacv:latest.integration",
    ivy"org.jline:jline:latest.integration"
  )
}
