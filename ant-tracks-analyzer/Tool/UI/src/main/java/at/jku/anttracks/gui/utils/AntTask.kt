
package at.jku.anttracks.gui.utils

import at.jku.anttracks.gui.model.ClientInfo
import io.micrometer.core.instrument.Timer
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.concurrent.Task
import java.util.concurrent.ExecutionException;

import java.util.logging.Level
import java.util.logging.Logger

abstract class AntTask<T> : Task<T>() {
    protected val LOGGER: Logger
    private val meter: Timer
    protected var cancelProperty: BooleanProperty

    private var operation: Operation? = null

    init {
        LOGGER = Logger.getLogger(this.javaClass.simpleName)
        cancelProperty = SimpleBooleanProperty(false)

        this.meter = ClientInfo.meterRegistry.timer(javaClass.simpleName + " - backgroundWork")

        updateProgress(0, 0)
        PlatformUtil.runAndWait { operation = Operation(this) }

        setOnCancelled { wse ->
            LOGGER.log(Level.SEVERE, "Cancelled AntTask.\n$wse")
            cancelProperty.value = true
        }

        setOnFailed { wse -> exception.printStackTrace() }
    }

    open fun showOnUI(): Boolean {
        return true
    }

    @Throws(Exception::class)
    override fun call(): T {
        if (showOnUI()) {
            ClientInfo.operationManager.addOperation(operation)
        }
        //Measurement m = ApplicationStatistics.getInstance().createMeasurement(getClass().getSimpleName() + " - backgroundWork");

        val ret = backgroundWork()
        //m.end();
        //meter.record(m.getDuration(), TimeUnit.NANOSECONDS);
        operation!!.close()
        return ret
    }

    @Throws(Exception::class)
    protected abstract fun backgroundWork(): T

    override fun succeeded() {
        //Measurement m = ApplicationStatistics.getInstance().createMeasurement(getClass().getSimpleName() + " - suceeded");
        finished()
        //m.end();
    }

    protected abstract fun finished()
}
