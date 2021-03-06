package controllers

import javax.inject.Inject
import javax.inject.Singleton

import models.adt.NoteObj
import models.database._
import models.formdata.{AddCollaboratorData, NewProjectNote, TableSQLScript}
import models.jsonmodels.ManualRowAddContent
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, text}
import play.api.db.Database
import play.api.mvc._

/**
  * Created by nathanhanak on 7/16/17.
  */
@Singleton
class ProjectsWorkSpaceController @Inject()(twDB: Database, authController: AuthenticationController, cc: ControllerComponents) extends AbstractController(cc) with play.api.i18n.I18nSupport {

  /**
    * loads main project workspace page
    *
    * @param projectTitle the name of the current project of the user
    * @return an HTTP response containing the HTML for the project workspace
    */
  def loadWorkspace(projectTitle: String) = Action {
    implicit request: Request[AnyContent] =>
      if (ProjectPermissions.projectExists(projectTitle, twDB)) {
        val userName = request.session.get("username").getOrElse("No User Found in Session")
        if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 400, twDB)) {
          val desiredPage = views.html.afterLogin.projectworkspace.projectView(projectTitle)
          authController.returnDesiredPageIfAuthenticated(request, desiredPage)
            .addingToSession("projectTitle" -> projectTitle)
        } else {
          Forbidden(views.html.afterLogin.projectworkspace.noPermissionFullPage())
        }
      } else {
        NotFound(views.html.afterLogin.projectworkspace.projectDoesNotExist(projectTitle))
      }
  }

  /**
    * Loads the template which will allows the user to add new .csv or .xls data to their database
    * Will eventually nest a newDataUploader or dataAppender inside
    *
    * @return an HTTP response containing the HTML for the table data uploader
    */
  def renderDataImporterSelector() = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("username").getOrElse("No User Found in Session")
      val projectTitle = request.session.get("projectTitle").getOrElse("Project Not Found")
      if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 249, twDB))
        Ok(views.html.afterLogin.projectworkspace.dataImporterSelector())
      else Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
  }


  /**
    * Loads the template which allows the user to add new .csv or .xls data to their database,
    * appending it to an already-existing table
    *
    * @return an HTTP response containing the HTML for the table data appender
    */
  def renderDataAppender() = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("username").getOrElse("No User Found in Session")
      val projectTitle = request.session.get("projectTitle").getOrElse("Project Not Found")
      if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 249, twDB))
        Ok(views.html.afterLogin.projectworkspace.dataAppender())
      else Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
  }

  /**
    * Loads a template which will allow a user to select their data saved in the DB
    * and view it in the project workspace
    *
    * @return
    */
  def renderDataPickerTool() = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("username").getOrElse("No User Found in Session")
      val projectTitle = request.session.get("projectTitle").getOrElse("Project Not Found")
      if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 401, twDB))
        authController.returnDesiredPageIfAuthenticated(
          request,
          views.html.afterLogin.projectworkspace.dataPickerTool(
            DataRetriever.retrieveAllProjectData(projectTitle, twDB)))
      else Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
  }

  /**
    * Loads a template containing all the notes for a project which will load in the project workspace
    *
    * @param projectTitle the name of the current project
    * @return the view which will contain all of the notes for the project
    */
  def getAllNotes(projectTitle: String) = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("username").getOrElse("No User Found in Session")
      if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 399, twDB)) {
        val allProjectNotes: List[NoteObj] = ProjectNotesData.getAllProjectNotes(projectTitle, twDB)
        Ok(views.html.afterLogin.projectworkspace.projectNotes(allProjectNotes))
      } else {
        Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
      }
  }

  /**
    * Method which retrieves a view which will contain the desired table AND WORKSPACE
    * for the project by name
    *
    * @param tableName the SQL formatted name of the data table we are tryign to retrieve
    * @return an HTML view containing the table in a viewable format for the project workspace
    */
  def renderProjectDataTable(tableName: String) = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("username").getOrElse("No User Found in Session")
      val project = request.session.get("projectTitle").getOrElse("No Project Title Found in Session")
      if (ProjectPermissions.userHasPermissionLevel(userName, project, 399, twDB)) {
        val fullTable = DataRetriever.retrieveFullDataTableByName(tableName, twDB)
        Ok(views.html.afterLogin.projectworkspace.tableBoxProjDataWorkspace(fullTable, tableName))
      } else {
        Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
      }
  }

  /**
    * Method for Ajax calls which will only retern an HTML template containing the data
    * table requested, but only the bare minimum - what's between and including the <table> tags
    *
    * @param tableName the name of the table to retrieve in full
    * @return a view html template containing the minimum HTML containing the <table>
    */
  def renderOnlyTableByName(tableName: String) = Action {
    implicit request: Request[AnyContent] => {
      val fullTable = DataRetriever.retrieveFullDataTableByName(tableName, twDB)
      Ok(views.html.afterLogin.projectworkspace.projDataTableOnly(fullTable, tableName))
    }
  }

  /**
    * Method which will load the form allowing the user to append a note to the current project
    *
    * @return an Ok request containing the view template with the form to enter a new note
    */
  def renderNoteAdder() = Action {
    implicit request: Request[AnyContent] =>
      if (authController.userHasRequiredPermissionLevel(299, request) && authController.sessionIsAuthenticated(request.session)) {
        Ok(views.html.afterLogin.projectworkspace.newNotePostForm(newProjNote))
      } else {
        Unauthorized(views.html.afterLogin.projectworkspace.noPermissionSmall())
      }
  }


  val newProjNote: Form[NewProjectNote] = Form {
    mapping(
      "projectTitle" -> nonEmptyText,
      "noteTitle" -> text,
      "noteAuthor" -> nonEmptyText,
      "noteContent" -> nonEmptyText,
    )(NewProjectNote.apply)(NewProjectNote.unapply)
  }

  /**
    * Method which turns user input for a note into a entry into the DB for notes for the project
    *
    * @return an Action result based on if the user is authorized, has permission, or is successful
    */
  def postNewNoteToDb() = Action { implicit request: Request[AnyContent] =>
    if (authController.sessionIsAuthenticated(request.session)) {
      newProjNote.bindFromRequest().fold(
        errorForm => BadRequest("Form data did not bind"),
        successForm => {
          val columnsTovals: Map[String, String] = mapSQLColumnLabelsToNoteFormFields(successForm)
          if (DatabaseUpdate.insertRowInto(twDB, "project_notes", columnsTovals) == 1) Ok(views.html.afterLogin.projectworkspace.noteAddSuccess())
          else {
            BadRequest("Unable to insert note")
          }
        }
      )
    } else {
      Unauthorized(views.html.expiredSession("You have been logged out or your session has expired"))
    }
  }


  /**
    * Take the fields collected from form and place into values for a map where the SQL table
    * column names are the keys
    *
    * @param note the new note data type containing the note fields
    * @return a Map of keys:SQL Columns to values: form fields
    */
  def mapSQLColumnLabelsToNoteFormFields(note: NewProjectNote): Map[String, String] = {
    Map(
      "project_title" -> note.projectTitle,
      "note_title" -> note.noteTitle,
      "note_author" -> note.noteAuthor,
      "note_content" -> note.noteContent
    )
  }

  /**
    * Method which retrieves all data related to collaborators for the project
    * And generates the view responsible for displaying it back to the user.
    *
    * @return
    */
  def renderCollaboratorViewer() = Action {
    implicit request: Request[AnyContent] =>
      if (!authController.sessionIsAuthenticated(request.session)) {
        Ok(views.html.expiredSession("Not Authenticated"))
      } else {
        val projectTitle = request.session.get("projectTitle").getOrElse("No Project Title found in Session")
        val collaboratorTable = mapPermissionNumToDescription(DataRetriever.retrieveCollaboratorsForProject(projectTitle, twDB))
        Ok(views.html.afterLogin.projectworkspace.collaboratortools.viewCollaboratorsTool(collaboratorTable))
      }
  }

  /**
    * Recursive Method iterates over a List of collaborator permission values, replacing the number value
    * with a text description
    *
    * @param permissions a List of Arrays of Strings representing Collaborations table
    *                    Each Array is a row of  UserNames and Permission Level
    * @param accum       an intially empty result accumulator
    * @return the accumulated List once permissions is empty. 
    */
  def mapPermissionNumToDescription(permissions: List[Array[String]], accum: List[Array[String]] = List[Array[String]]()): List[Array[String]] = permissions match {
    case xs if xs.isEmpty => accum
    case h :: t if h(1).equals("100") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "Project Lead: All permissions")))
    case h :: t if h(1).equals("200") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "Project Contributor: View all, add notes, upload data")))
    case h :: t if h(1).equals("250") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "Project Contributor: View all, add notes, no table data upload/addition")))
    case h :: t if h(1).equals("300") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "External Viewer: View all data - no write or upload")))
    case h :: t if h(1).equals("400") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "Public: View only")))
    case h :: t if h(1).equals("999") => mapPermissionNumToDescription(t, accum ::: List(Array(h(0), "Forbidden Access")))
  }

  /**
    * Method receives a request containing the string of the user we wish to remove as collaborator
    *
    * @param userName the user we would like to remove from the collaborations table
    * @return an OK response which will reload the ViewCollaborator tool, containing a new version of the table
    *         with the desired user removed.
    */
  def removeCollaborator(userName: String) = Action {
    implicit request: Request[AnyContent] =>
      val projectTitle = request.session.get("projectTitle").getOrElse("No Project Title found in Session")
      if (authController.userHasRequiredPermissionLevel(199, request) && authController.sessionIsAuthenticated(request.session)) {
        if (DatabaseUpdate.removeFromTable(s"username='$userName' AND project_title='$projectTitle'", "collaborations", twDB)) {
          val collaboratorTable = mapPermissionNumToDescription(DataRetriever.retrieveCollaboratorsForProject(projectTitle, twDB))
          Ok(views.html.afterLogin.projectworkspace.collaboratortools.viewCollaboratorsTool(collaboratorTable))
        } else {
          val collaboratorTable = mapPermissionNumToDescription(DataRetriever.retrieveCollaboratorsForProject(projectTitle, twDB))
          BadRequest(views.html.afterLogin.projectworkspace.collaboratortools.viewCollaboratorsTool(collaboratorTable))
        }
      } else {
        val collaboratorTable = mapPermissionNumToDescription(DataRetriever.retrieveCollaboratorsForProject(projectTitle, twDB))
        BadRequest(views.html.afterLogin.projectworkspace.collaboratortools.viewCollaboratorsTool(collaboratorTable))
      }
  }

  val addCollabForm: Form[AddCollaboratorData] = Form {
    mapping(
      "userToAdd" -> nonEmptyText,
      "permissionSelection" -> nonEmptyText
    )(AddCollaboratorData.apply)(AddCollaboratorData.unapply)
  }

  //case class AddCollaboratorData(userToAdd:String, permissionSelection:String)

  /**
    * Method which accepts the POST request with the addNewCollaboratorForm.
    *
    * @return a Result indicating the success or failure of the attempted post
    */
  def addCollaborator() = Action {
    implicit request: Request[AnyContent] =>
      addCollabForm.bindFromRequest().fold(
        errorForm => BadRequest(views.html.afterLogin.projectworkspace.itemAddFail("new collaborator")),
        collabForm => {
          if (DataRetriever.userExists(collabForm.userToAdd, twDB) && authController.userHasRequiredPermissionLevel(199, request)) {
            val projectTitle = request.session.get("projectTitle").getOrElse("No Project Title found in session")
            val rowsInserted = DatabaseUpdate.insertRowInto(twDB, "collaborations",
              Map("username" -> collabForm.userToAdd,
                "project_title" -> request.session.get("projectTitle").getOrElse("No Project Title found in session"),
                "permission_level" -> collabForm.permissionAsInt().toString))
            val collaboratorTable = mapPermissionNumToDescription(DataRetriever.retrieveCollaboratorsForProject(projectTitle, twDB))
            if (rowsInserted == 1 ) {
              Ok(views.html.afterLogin.projectworkspace.collaboratortools.viewCollaboratorsTool(collaboratorTable))
            } else {
              BadRequest("Unable to add collaborator, try again")
            }
          }
          else {
            BadRequest(s"Unable to find user you entered: ${collabForm.userToAdd}")
          }
        }
      )
  }

  /**
    * Method which retrieves tools needed to process the data tables in the project viewspace.
    * Performs authentcation and permission checks first.
    *
    * @param toolNeeded the name of analysis tool which we would like to return to the user
    * @return a view template containing the tool requested
    */
  def tableToolFactory(toolNeeded: String, tableName: String) = Action {
    implicit request: Request[AnyContent] => {
      //auth and permission check first
      if (!authController.sessionIsAuthenticated(request.session)) {
        Ok(views.html.expiredSession("Not Authenticated"))
      } else if (!authController.userHasRequiredPermissionLevel(249, request)) {
        Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
      } else {
        // return desired tool page
        toolNeeded match {
          case "tableQuery" =>
            Ok(views.html.afterLogin.projectworkspace.querytool.tableQueryFormView(queryForm, SQLViewsQueryExecutor.generateViewFor(tableName, twDB))
            )
          case "gmaps" => Ok(views.html.afterLogin.projectworkspace.generateGoogleMaps())
          case "manuallyAddRow" =>
            val tableHeaders = DataRetriever.getTableheaders(tableName, twDB).toList
            Ok(views.html.afterLogin.projectworkspace.manualAddDataRow(tableHeaders, tableName))
          case _ => NotFound("No page found for request")
        }
      }
    }
  }

  /**
    * Method which receives the POST request for the row which manually
    * adds a new row to the desired table
    *
    * @return an Ok response if the insert was succesful.
    */
  def manualAddNewRow(tableName: String) = Action(parse.json[List[ManualRowAddContent]]) {
    implicit request => {
      val myContentList: List[ManualRowAddContent] = request.body
      var colsToVals = scala.collection.mutable.Map[String, String]()
      myContentList.foreach(content => colsToVals += (content.colName -> content.value))
      colsToVals += ("uploaded_by" -> request.session.get("username").getOrElse("unknown"))
      val rowsAffected = DatabaseUpdate.insertRowInto(twDB, tableName, colsToVals.toMap)
      if (rowsAffected == 1) {
        Ok("Row added successfully.")
      } else {
        BadRequest(s"DB insert updated $rowsAffected rows, and it should have been only 1")
      }
    }
  }

  /**
    * Method which returns a successful or failure notification for the adding the item
    *
    * @param item the name of the item which was added by the user
    * @return the view template containing an appropriate message.
    */
  def getTableToolResponse(item: String, success: Boolean) = Action {
    implicit request: Request[AnyContent] =>
      success match {
        case true => Ok(views.html.afterLogin.projectworkspace.itemAddSuccess(item))
        case false => Ok(views.html.afterLogin.projectworkspace.itemAddFail(item))
      }
  }

  //form which handles a user-created query from the Project Workspace
  val queryForm: Form[TableSQLScript] = Form {
    mapping(
      "viewName" -> nonEmptyText,
      "query" -> nonEmptyText
    )(TableSQLScript.apply)(TableSQLScript.unapply)
  }

  /**
    * Method handles the POSTing of a form for the custom query creator.
    * Returns a view template containing a view which renders the resulting SQL
    *
    * @return a view template containing the result of the query, or an error view if it didn't work.
    */
  def postQueryReturnResult() = Action {
    implicit request: Request[AnyContent] =>
      queryForm.bindFromRequest().fold(
        errorForm => BadRequest("Unable to process your input values, please try again."),
        qryForm => {
          val viewName = qryForm.viewName
          val (qryResult: List[Array[String]], message) = DataRetriever.performQueryOnView(qryForm, twDB)
          (qryResult, message) match {
            case (qR, msg) if qR.nonEmpty => Ok(views.html.afterLogin.projectworkspace.projDataTableOnly(qR, viewName))
            case (qR, msg) if (qR.isEmpty && msg.equals("clean query")) => BadRequest("No results returned, try another query.")
            case (qR, msg) if (qR.isEmpty && msg.equals("clean query")) => BadRequest(msg)
            case _ => BadRequest(s"Error: $message")
          }
        }
      )
  }

}
