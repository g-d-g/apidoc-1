package lib

import com.bryzek.apidoc.api.v0.models.{OrganizationForm, Visibility}
import org.scalatest.{FunSpec, Matchers}
import org.junit.Assert._
import java.util.UUID

class UtilSpec extends FunSpec with Matchers with TestApplication {

  it("validateReturnUrl for invalid domains") {
    Util.validateReturnUrl("") should be(Left(Seq("Redirect URL[] must start with / or a known domain")))
    Util.validateReturnUrl("https://google.com/foo") should be(Left(Seq("Redirect URL[https://google.com/foo] must start with / or a known domain")))
  }

  it("validateReturnUrl for valid domains") {
    Util.validateReturnUrl("/") should be(Right("/"))
    Util.validateReturnUrl("https://www.apidoc.me") should be(Right("/"))
    Util.validateReturnUrl("https://www.apidoc.me/") should be(Right("/"))
  }

}
