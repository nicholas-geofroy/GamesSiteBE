package controllers

import javax.inject.Singleton
import javax.inject.Inject
import play.api.mvc.Action
import play.api.mvc.AbstractController
import play.api.mvc.ControllerComponents

@Singleton
class ApplicationController @Inject() (
    val cc: ControllerComponents
) extends AbstractController(cc) {
  def options(path: String) = Action.apply(parse.json) {
    Ok("")
  }
}
