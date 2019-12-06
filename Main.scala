import java.sql.DriverManager
import scala.sys.process.Process
import scala.util.control.NonFatal

object Main {
  private[this] def using[A <: AutoCloseable, B](a: A)(f: A => B): B = {
    try {
      f(a)
    } finally {
      a.close()
    }
  }

  def prepareDocker(v: String): Unit = {
    val name = "mysql-db-test"

    try {
      println("force stop docker")
      Process(Seq("docker", "rm", "-f", name)).!
    } catch {
      case NonFatal(e) =>
        println(e)
    }

    println("launch docker")
    val command = Seq(
      "docker",
      "run",
      "--name",
      name,
      "-e",
      "MYSQL_USER=test",
      "-e",
      s"MYSQL_PASSWORD=${password}",
      "-e",
      s"MYSQL_ROOT_PASSWORD=${password}",
      "-p",
      s"$port:3306",
      "-d",
      "mysql:" + v,
      "--max_connections=1000",
      "--character-set-server=utf8",
      "--collation-server=utf8_unicode_ci"
    )
    println(command)
    Process(command).!
  }

  val testDBName = "hoge"
  val password = "admin"
  val port = 12345

  def createDatabase(dbName: String): Unit = {
    println(s"create database ${dbName}")
    def loop(n: Int): Unit = {
      try {
        val url = s"jdbc:mysql://127.0.0.1:${port}/mysql?useUnicode=true&characterEncoding=UTF-8"
        using(DriverManager.getConnection(url, "root", password)) { connection =>
          using(connection.createStatement()) {
            _.executeUpdate(s"DROP DATABASE IF EXISTS $dbName")
          }
          using(connection.createStatement()) {
            _.executeUpdate(s"CREATE DATABASE $dbName")
          }
        }
      } catch {
        case NonFatal(e) if n < 20 =>
          println(s"retry $e")
          Thread.sleep(1000)
          loop(n + 1)
      }
    }
    loop(1)
  }

  def main(args: Array[String]): Unit = {
    prepareDocker(sys.env("MYSQL_VERSION"))
    createDatabase(testDBName)

    val url = s"jdbc:mysql://127.0.0.1:${port}/${testDBName}?useUnicode=true&characterEncoding=UTF-8"
    using(DriverManager.getConnection(url, "root", password)) { connection =>
      using(connection.createStatement()) {
        _.executeUpdate(s"""
          |create table aaa(
          |`id`  VARCHAR(50) BINARY NOT NULL PRIMARY KEY,
          |`yyy` TIMESTAMP(3) DEFAULT NULL
          |)""".stripMargin)
      }
    }
  }
}
