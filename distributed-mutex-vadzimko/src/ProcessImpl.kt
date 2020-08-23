package mutex

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Vadim Badyaev
 */
class ProcessImpl(private val env: Environment) : Process {
    private var status = Status.Sleep
    private var maxForks = env.nProcesses - 1
    private var forksCounter = env.nProcesses - env.processId

    private var forks = BooleanArray(env.nProcesses + 1)
    private var dirty = BooleanArray(env.nProcesses + 1)
    private var asked = BooleanArray(env.nProcesses + 1)

    init {
        for (i in 1..env.nProcesses) {
            dirty[i] = false
            forks[i] = i >= env.processId
            asked[i] = false
        }
    }

    override fun onMessage(srcId: Int, message: Message) {
        val msg = message.parse { readEnum<MsgType>() }

        if (msg == MsgType.Ask) {
            check(forks[srcId])

            if (dirty[srcId] && status != Status.CS) {
                env.send(srcId) { writeEnum(MsgType.Share) }
                forks[srcId] = false
                forksCounter--

                if (status == Status.Wait) {
                    env.send(srcId) { writeEnum(MsgType.Ask) }
                }
            } else {
                asked[srcId] = true
            }
        } else {
            check(!forks[srcId])

            forksCounter++
            forks[srcId] = true
            dirty[srcId] = false

            if (forksCounter == maxForks) {
                status = Status.CS
                env.locked()
            }
        }
    }

    override fun onLockRequest() {
        check(status == Status.Sleep)
        status = Status.Wait

        if (forksCounter == maxForks) {
            status = Status.CS
            env.locked()
            return
        }

        for (i in 1..env.nProcesses) {
            if (i == env.processId) continue
            if (!forks[i]) {
                env.send(i) {
                    writeEnum(MsgType.Ask)
                }
            }

        }
    }

    override fun onUnlockRequest() {
        check(status == Status.CS)
        env.unlocked()

        for (i in 1..env.nProcesses) {
            if (asked[i]) {
                forksCounter--
                env.send(i) { writeEnum(MsgType.Share) }
                forks[i] = false
                asked[i] = false
            } else {
                dirty[i] = true
            }
        }

        status = Status.Sleep
    }

    enum class MsgType { Share, Ask }
    enum class Status { Sleep, Wait, CS }
}
