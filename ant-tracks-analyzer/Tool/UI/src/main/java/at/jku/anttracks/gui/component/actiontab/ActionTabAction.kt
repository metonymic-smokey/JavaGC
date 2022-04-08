package at.jku.anttracks.gui.component.actiontab

import javafx.beans.binding.BooleanExpression
import javafx.beans.property.SimpleBooleanProperty
import javax.swing.Icon

class ActionTabAction(val name: String,
                      val description: String? = null,
                      val category: String? = null,
                      val enabled: BooleanExpression = SimpleBooleanProperty(true),
                      val icon: Icon? = null,
                      val function: () -> Unit)