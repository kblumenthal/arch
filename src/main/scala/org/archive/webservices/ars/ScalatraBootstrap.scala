package org.archive.webservices.ars

import org.scalatra.LifeCycle

import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext): Unit = {
    context.mount(new AdminController, "/admin/*")
    context.mount(new ApiController, "/api/*")
    context.mount(new WasapiController, "/wasapi/*")
    context.mount(new FilesController, "/files/*")
    context.mount(new DefaultController, "/*")
  }
}
