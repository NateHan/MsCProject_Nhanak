package controllers

import java.sql.Statement
import javax.inject.{Inject, Singleton}

import models.database.{DatabaseUpdate, DbInputValidator, LoginInputsValidator}
import models.formdata.{RegistrationData, UserLoginData}
import play.api.data.Form
import play.api.data.Forms._
import play.api.db.Database
import play.api.mvc._
import play.filters.csrf.CSRF
import play.filters.headers.SecurityHeadersFilter

/**
  * Created by nathanhanak on 7/7/17.
  */
@Singleton
class LoginRegController @Inject()(twDB: Database, cc: ControllerComponents, authCntrlr: AuthenticationController) extends AbstractController(cc) with play.api.i18n.I18nSupport {


  def loadLogin() = Action {
      implicit request: Request[AnyContent] =>
        Ok(views.html.login(loginform)).withNewSession.withHeaders(SecurityHeadersFilter
          .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com maps.googleapis.com")
  }

  val loginform: Form[UserLoginData] = Form(
    mapping(
      "inputEmail" -> nonEmptyText,
      "inputPassword" -> nonEmptyText,
      "rememberLogin" -> boolean
    )(UserLoginData.apply)(UserLoginData.unapply)
  )

  /**
    * runs when user clicks login, sending a post request with the data from the login fields
    * Runs user through authentication check, if passes attaches user details to
    * session cookie
    *
    * @return the Action for the resulting page
    */
  def attemptLogin() = Action {
     implicit request: Request[AnyContent] => {
      loginform.bindFromRequest().fold(
        formWithErrors => BadRequest(views.html.login(formWithErrors)),
        successfulForm => {
          val validator: DbInputValidator = new LoginInputsValidator(twDB, successfulForm)
          if (validator.inputsAreValid) {
            val userName = getUserName(successfulForm.inputEmail.toLowerCase)
            Ok(views.html.loginSuccess())
              .withSession(request.session + ("authenticated" -> "true") + ("username" -> userName))
              .withHeaders(SecurityHeadersFilter
                .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com maps.googleapis.com")
          } else {
            BadRequest(views.html.login(loginform))
          }
        }
      )
    }
  }

  /**
    * retrieves the user name for display in the logged in navbar
    *
    * @param email the successfully logged in email handle for the user
    * @return the username in string form
    */
  private def getUserName(email: String): String = {
    twDB.withConnection { conn =>
      val prepStmt = conn.prepareStatement("SELECT userName FROM verified_users WHERE uemail=?")
      prepStmt.setString(1, email)
      val qryResult = prepStmt.executeQuery()
      var userName: String = ""
      while (qryResult.next()) {
        userName = qryResult.getString("userName")
      }
      userName
    }
  }

  val regForm: Form[RegistrationData] = Form(
    mapping(
      "uEmail" -> nonEmptyText,
      "uPassword" -> nonEmptyText,
      "userName" -> nonEmptyText,
      "fullName" -> nonEmptyText,
      "organization" -> text
    )(RegistrationData.apply)(RegistrationData.unapply)
  )

  /**
    * Loads the view containing the registration form
    *
    * @return the view containing the registration form
    */
  def loadRegistrationPage() = Action {
    implicit request: Request[AnyContent] =>
      Ok(views.html.register(regForm)).withHeaders(SecurityHeadersFilter
        .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com")
  }

  /**
    * The method called by POST to URL: /register
    * Receives user-input from form and adds it to the database of verified_users
    *
    * @return reloads the page on a bad request, brings to login on a good request
    */
  def registerSubmit() = Action { implicit request: Request[AnyContent] =>
    regForm.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.register(regForm)).withHeaders(SecurityHeadersFilter
        .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com"),
      successfulForm => {
        val result = inputRegInfoToDB(successfulForm)
        if (result == 1) {
          Ok(views.html.regThanksRedirect()).withHeaders(SecurityHeadersFilter
            .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com").withNewSession
        } else {
          BadRequest(views.html.register(regForm)).withHeaders(SecurityHeadersFilter
            .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com")
        }
      }
    )
  }

  /**
    * Enters the registration form data into the DB.
    *
    * @param userData the data extracted from the user form
    * @return the number of rows inserted in the DB, should only be 1
    */
  private def inputRegInfoToDB(userData: RegistrationData): Int = {
    DatabaseUpdate.insertRowInto(twDB, "verified_users",
      Map("uemail" -> userData.uEmail.toLowerCase,
        "upassword" -> userData.uPassword,
        "username" -> userData.userName,
        "fullname" -> userData.fullName,
        "organization" -> userData.organization)
    )
  }

  /**
    * Brings user to landing page, giving them a new session cookie to fully logout.
    *
    * @return
    */
  def logOut = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
      .withNewSession
      .withHeaders(SecurityHeadersFilter
        .CONTENT_SECURITY_POLICY_HEADER -> " .fontawesome.com .fonts.googleapis.com")
  }
}

