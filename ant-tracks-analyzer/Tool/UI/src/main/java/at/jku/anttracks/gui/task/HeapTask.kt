
package at.jku.anttracks.gui.task

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.WindowUtil
import at.jku.anttracks.heap.Heap
import at.jku.anttracks.heap.HeapAdapter
import at.jku.anttracks.heap.io.MetaDataReaderConfig
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.heap.HeapTraceParser
import at.jku.anttracks.util.TraceException
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonType
import javafx.scene.layout.Region
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.logging.Level

abstract class HeapTask<T : Heap>(protected val appInfo: AppInfo, protected val time: Long) : AntTask<T?>() {

    @Throws(Exception::class)
    override fun backgroundWork(): T? {
        try {
            val heap = parseTrace()
            additionalBackgroundWork(heap)
            return heap
        } catch (e: InterruptedException) {
            // task about to end after being cancelled
            handleTaskCancelled()
            return null
        } catch (e: Exception) {
            val alertTask = FutureTask {
                val alert = Alert(AlertType.ERROR,
                                  "An internal error occured while parsing, do you want to retry?\n$e",
                                  ButtonType.YES,
                                  ButtonType.NO)
                alert.title = "Error"
                alert.dialogPane.minHeight = Region.USE_PREF_SIZE
                WindowUtil.centerInMainFrame(alert)
                val retryChoice = alert.showAndWait()
                retryChoice.isPresent && retryChoice.get() == ButtonType.YES
            }
            Platform.runLater(alertTask)
            return if (alertTask.get()) {
                backgroundWork()
            } else {
                throw Exception(e)
            }
        }

    }

    protected abstract fun handleTaskCancelled()

    protected abstract fun additionalBackgroundWork(heap: T)

    @Throws(Exception::class)
    private fun parseTrace(): T {
        LOGGER.log(Level.INFO, "Start parsing trace")
        updateTitle("Heap State")
        updateMessage("Parsing trace file to reach heap state at time " + time / 1000.0 + "s")
        val parser = HeapTraceParser(appInfo.symbols, MetaDataReaderConfig(appInfo.symbols.root + File.separator + Consts.ANT_META_DIRECTORY), time)
        parser.addHeapListener(object : HeapAdapter() {
            override fun phaseChanging(
                    sender: Any,
                    from: ParserGCInfo,
                    to: ParserGCInfo,
                    failed: Boolean,
                    position: Long,
                    parsingInfo: ParsingInfo,
                    inParserTimeWindow: Boolean) {
                updateProgress(position - parsingInfo.fromByte, parsingInfo.traceLength)
            }
        })
        val heap = parse(parser)
        LOGGER.log(Level.INFO, "Finished parsing trace")
        return heap
    }

    @Throws(InterruptedException::class, IOException::class, TraceException::class, ExecutionException::class)
    protected abstract fun parse(parser: HeapTraceParser): T
}
