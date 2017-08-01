package models

import models.database.{DatabaseUpdate, DefaultDataBase}
import models.database.DatabaseUpdate._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite


class DataBaseUpdateSpec extends PlaySpec with GuiceOneAppPerSuite with BeforeAndAfterAll {

  val twDB = DefaultDataBase.getApplicationDataBase

  "DatabseUpdate object #insertInto " should {

    "return a value of 1 when inserting a single value " in {
      val tableName = "all_projects"
      val columnsAndValues = Map("project_title" -> "'Track Sharks 848305439'", "project_lead" -> "'DemoUser'")
      val expectedReturn = 1

      DatabaseUpdate.insertInto(tableName, columnsAndValues, expectedReturn) mustBe 1
      // remove test data
      twDB.withTransaction { conn =>
        val stmt = conn.createStatement
        val query = "DELETE FROM all_projects WHERE project_title='Track Sharks 848305439';"
        stmt.executeUpdate(query) mustBe 1
      }
    }
  }

}
