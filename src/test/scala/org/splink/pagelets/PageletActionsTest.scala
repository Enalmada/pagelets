package org.splink.pagelets

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import org.splink.pagelets.Exceptions.PageletException
import play.api.Environment
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import scala.language.reflectiveCalls

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class PageletActionsTest extends PlaySpec with OneAppPerSuite with MockitoSugar {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val env = Environment.simple()
  implicit val request = FakeRequest()

  def actions = new PageletActionsImpl with Controller with PageBuilder with TreeTools with Resources {

    override val builder: PageBuilderService = mock[PageBuilderService]

    val opsMock = mock[TreeOps]
    override implicit def treeOps(tree: Tree): TreeOps = opsMock

    override val resources: ResourceProvider = mock[ResourceProvider]
  }

  def leaf = mock[Leaf[_,_]]
  def tree(r: RequestHeader) = mock[Tree]

  def buildMock(service: PageBuilder#PageBuilderService)(ret: Future[PageletResult]) = when(service.build(
    any[Leaf[_, _]],
    anyVararg[Arg])(
    any[ExecutionContext],
    any[Request[AnyContent]],
    any[Materializer])).thenReturn(ret)

  "PageletAction" should {
    "return a Pagelet if the tree contains the pagelet for the given id" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(Some(leaf))
      buildMock(a.builder)(Future.successful(PageletResult("body")))

      val action = a.PageletAction(e => Html(s"${e.exception.getMessage}"))(tree, 'one) { (r, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "return NotFound if the tree does not contain a pagelet for the given id" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(None)
      buildMock(a.builder)(Future.successful(PageletResult("body")))

      val action = a.PageletAction(e => Html(s"${e.exception.getMessage}"))(tree, 'one) { (r, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(NOT_FOUND)
      contentAsString(result) must equal("'one does not exist")
    }

    "return InternalServerError if the tree fails to build" in {
      val a = actions
      when(a.opsMock.find('one)).thenReturn(Some(leaf))
      buildMock(a.builder)(Future.failed(new PageletException("something is wrong")))

      val action = a.PageletAction(e => Html(s"${e.exception.getMessage}"))(tree, 'one) { (r, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(INTERNAL_SERVER_ERROR)
      contentAsString(result) must equal("something is wrong")
    }

  }

  "PageAction" should {
    "return a Pagelet" in {
      val a = actions
      buildMock(a.builder)(Future.successful(PageletResult("body")))

      val action = a.PageAction(e => Html(s"${e.exception.getMessage}"))("title", tree) { (r, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)

      status(result) must equal(OK)
      contentAsString(result) must equal("body")
    }

    "return InternalServerError if the tree fails to build" in {
      val a = actions
      buildMock(a.builder)(Future.failed(new PageletException("something is wrong")))

      val action = a.PageAction(e => Html(s"${e.exception.getMessage}"))("title", tree) { (r, page) =>
        Html(s"${page.body}")
      }

      val result = action(request)
      status(result) must equal(INTERNAL_SERVER_ERROR)
      contentAsString(result) must equal("something is wrong")
    }

  }
}