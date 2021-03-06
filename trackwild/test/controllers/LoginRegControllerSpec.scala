package controllers


import akka.util.Timeout
import scala.concurrent.duration._
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneAppPerTest}
import play.api.db.Databases
import play.api.mvc.Result
import play.api.test.Helpers.{BAD_REQUEST, GET, OK, contentAsString, contentType, route, status, stubControllerComponents}
import play.api.test.{FakeRequest, Injecting}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by nathanhanak on 7/14/17.
  */
class LoginRegControllerSpec extends PlaySpec with GuiceOneAppPerSuite with Injecting {

  val testDb = Databases(
    driver = "org.postgresql.Driver",
    url = "postgres://twadmin:trackwild@localhost:5432/track_wild_db"
  )

  val controller = inject[LoginRegController]


  implicit val duration: Timeout = 20 seconds

  "LoginController /login GET" should {

    "render the login page from a new instance of LoginRegController" in {
      val loginPage = controller.loadLogin().apply(FakeRequest(GET, "/login"))

      status(loginPage) mustBe OK
      contentType(loginPage) mustBe Some("text/html")
      contentAsString(loginPage) must include("Track Wild: Login")
    }

    "render the login page from the application" in {
      val controller = inject[LoginRegController]
      val loginPage = controller.loadLogin().apply(FakeRequest(GET, "/login"))

      status(loginPage) mustBe OK
      contentType(loginPage) mustBe Some("text/html")
      contentAsString(loginPage) must include("Track Wild: Login")
    }

  }

  "LoginRegController POST /login " should {

    "return a BadRequest if user submits parameters in wrong format" in {
      implicit val request = FakeRequest("POST", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "notAnEmail", "inputPassword" -> "valid")

      val result: Future[Result] = controller.attemptLogin().apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "return a BadRequest if user submits a login not found in the database" in {
      implicit val request = FakeRequest("POST", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "does@notexist.com", "inputPassword" -> "nope")

      val result: Future[Result] = controller.attemptLogin().apply(request)
      status(result) mustBe BAD_REQUEST
    }

    "return OK if a user submits a valid login" in {
      implicit val request = FakeRequest("Post", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "demo@demo.com", "inputPassword" -> "demopassword")
      val result: Future[Result] = controller.attemptLogin().apply(request)
      status(result) mustBe OK

      implicit val request2 = FakeRequest("Post", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "nathan.hanak@gmail.com", "inputPassword" -> "trackwild")
      val result2: Future[Result] = controller.attemptLogin().apply(request2)
      status(result2) mustBe OK
    }

    "return a session containing expected header values " in {
      implicit val request = FakeRequest("Post", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "demo@demo.com", "inputPassword" -> "demopassword")
      val futResult: Future[Result] = controller.attemptLogin().apply(request)
      var authenticated: String = ""
      var userName: String = ""
      futResult.map(result => authenticated = result.session.get("authenticated").getOrElse("Nope"))
      futResult.map(result => userName = result.session.get("username").getOrElse("no username"))

      authenticated mustBe "true"
      userName mustBe "DemoUser"
    }
      // check again to be safe
    "return a session containing expected header values (second check) " in {
      implicit val request2 = FakeRequest("Post", "/login")
        .withFormUrlEncodedBody("inputEmail" -> "nathan.hanak@gmail.com", "inputPassword" -> "trackwild")
      val futResult2: Future[Result] = controller.attemptLogin().apply(request2)
      var authenticated2: String = ""
      var userName2: String = ""
      futResult2.map(result => authenticated2 = result.session.get("authenticated").getOrElse("Nope"))
      futResult2.map(result => userName2 = result.session.get("username").getOrElse("no username"))

      authenticated2 mustBe "true"
      userName2 mustBe "nhanak"
    }

  }

}
