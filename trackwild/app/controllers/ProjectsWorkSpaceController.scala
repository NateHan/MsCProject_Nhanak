package controllers

import javax.inject.Inject
import javax.inject.Singleton

import models.adt.NoteObj
import models.database.{ProjectNotesData, ProjectPermissions}
import play.api.db.Database
import play.api.mvc._
import play.filters.headers.SecurityHeadersFilter

/**
  * Created by nathanhanak on 7/16/17.
  */
@Singleton
class ProjectsWorkSpaceController @Inject()(twDB: Database, authController: AuthenticationController, cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * loads main project workspace page
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
    * @return an HTTP response containing the HTML for the table data uploader
    */
  def renderDataImporterSelector() = Action {
    implicit request: Request[AnyContent] =>
      val userName = request.session.get("userName").getOrElse("No User Found in Session")
      val projectTitle = request.session.get("projectTitle").getOrElse("Project Not Found")
      if (ProjectPermissions.userHasPermissionLevel(userName, projectTitle, 249, twDB))
        Ok(views.html.afterLogin.projectworkspace.dataImporterSelector())
      else Ok(views.html.afterLogin.projectworkspace.noPermissionSmall())
  }


  /**
    * Loads the template which allows the user to add new .csv or .xls data to their database,
    * appending it to an already-existing table
    * @return an HTTP response containing the HTML for the table data appender
    */
  def renderDataAppender() = Action {
    implicit request: Request[AnyContent] => Ok(views.html.afterLogin.projectworkspace.dataAppender())
  }

  /**
    * Loads a template which will allow a user to select their data saved in the DB
    * and view it in the project workspace
    * @return
    */
  def renderDataViewerTool() = Action {
    implicit request: Request[AnyContent] => Ok(views.html.afterLogin.projectworkspace.dataViewerTool())
  }

  /**
    * Loads a template containing all the notes for a project which will load in the project workspace
    * @param projectTitle the name of the current project
    * @return the view which will contain all of the notes for the project
    */
  def getAllNotes(projectTitle:String) = Action {
    implicit request : Request[AnyContent] =>
      val allProjectNotes: List[NoteObj] = ProjectNotesData.getAllProjectNotes(projectTitle, twDB)
      Ok(views.html.afterLogin.projectworkspace.projectNotes(allProjectNotes))
  }
}
