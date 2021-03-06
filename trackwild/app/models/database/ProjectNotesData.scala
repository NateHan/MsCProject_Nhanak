package models.database

import models.adt.NoteObj
import play.api.db.Database

import scala.collection.mutable.ListBuffer

object ProjectNotesData {

  /**
    * Retrieves all the notes for the given project
    * @param projectTitle the project title
    * @param db the application's database
    * @return a list of NoteObj datatypes, each containing an instance of a note
    */
  def getAllProjectNotes(projectTitle:String, db: Database): List[NoteObj] = {
    val noteObjList = new ListBuffer[NoteObj]
    db.withConnection{ conn =>
      val prepStmt = conn.prepareStatement("SELECT note_author, note_title, note_date, note_content FROM project_notes " +
        "WHERE project_title=? ORDER BY note_date DESC")
      prepStmt.setString(1, projectTitle)
      val qryResult = prepStmt.executeQuery()
      while (qryResult.next()) {
        val longTimeStamp = qryResult.getString("note_date")
        noteObjList += NoteObj(
          qryResult.getString("note_author"),
          qryResult.getString("note_title"),
          longTimeStamp.substring(0, longTimeStamp.indexOfSlice(".")), // removes sub second precision from timestamp
          qryResult.getString("note_content"))
      }
    }
    noteObjList.toList
  }

}
