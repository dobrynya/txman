package ru.txman

import java.util.UUID

/**
  * Provides utility methods.
  * Created by dmitry on 07.06.16.
  */
package object actors {

  /**
    * Just a helper method to create random UUID value.
    * @return
    */
  def uuid = UUID.randomUUID().toString
}
