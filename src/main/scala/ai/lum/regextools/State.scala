package ai.lum.regextools

import scala.collection.mutable._

class State {

  /** true if this is an accepting state */
  var accepting: Boolean = false

  val transitions: HashMap[String, State] = HashMap.empty

  /** returns array with all the states reachable from this state including this state */
  def reachableStates: Array[State] = {
    val states = ArrayBuffer.empty[State]
    val visited = LinkedHashSet.empty[State]
    val queue = Queue.empty[State]
    queue.enqueue(this)
    while (queue.nonEmpty) {
      val s = queue.dequeue()
      if (!visited.contains(s)) {
        visited += s
        states += s
        queue.enqueue(s.transitions.values.toSeq: _*)
      }
    }
    states.toArray
  }

  /** returns dot representation for graphviz */
  def mkDot: String = {
    val states = reachableStates
    def id(s: State): String = states.indexOf(s).toString()
    val accepting = states.filter(_.accepting).map(id).mkString(" ")
    val edges = for {
      src <- states
      (t, dst) <- src.transitions
    } yield s"""${id(src)} -> ${id(dst)} [label = "$t"]"""
    s"""digraph {
       |  rankdir = LR
       |  node [shape = doublecircle]
       |  $accepting
       |  node [shape = circle]
       |  ${edges.mkString("\n  ")}
       |}""".stripMargin
  }

  /** Hopcroft's algorithm */
  def minimize: State = {
    val Q = LinkedHashSet(reachableStates: _*) // all states
    val F = Q.filter(_.accepting) // final states
    val P = LinkedHashSet(F, Q diff F) // partitions
    val W = LinkedHashSet(F, Q diff F)
    // FIXME this is not really hopcroft's algorithm
    // see slides 6-7 here:
    //   https://people.mpi-inf.mpg.de/~horbach/teaching/2013SS/automata/3%20minimizing%20n%20log%20n.pdf

    val incomingTransitions: HashMap[State, HashMap[String, LinkedHashSet[State]]] = HashMap.empty
    for {
      src <- Q
      (t, dst) <- src.transitions
    } {
      incomingTransitions
        .getOrElseUpdate(dst, HashMap.empty)
        .getOrElseUpdate(t, LinkedHashSet.empty)
        .add(src)
    }

    while (W.nonEmpty) {

      // pop element of W
      val A = W.head
      W -= A

      // collect transitions into any member of A
      val trans: HashMap[String, LinkedHashSet[State]] = HashMap.empty
      for {
        s <- A
        (t, ss) <- incomingTransitions.getOrElseUpdate(s, HashMap.empty)
      } {
        trans.getOrElseUpdate(t, LinkedHashSet.empty) ++= ss
      }

      for {
        X <- trans.values
        Y <- P
      } {
        val i = X intersect Y
        val d = Y diff X
        if (i.nonEmpty && d.nonEmpty) {
          P -= Y
          P += i
          P += d
          if (W contains Y) {
            W -= Y
            W += i
            W += d
          } else if (i.size <= d.size) {
            W += i
          } else {
            W += d
          }
        }
      }
    }

    val newStates: HashMap[LinkedHashSet[State], State] = HashMap.empty
    var start: State = null

    for (S <- P) {
      val s = newStates.getOrElseUpdate(S, new State)
      for ((t, dst) <- S.head.transitions) {
        val p = P.find(_ contains dst).get
        s.transitions(t) = newStates.getOrElseUpdate(p, new State)
      }
      s.accepting = S.head.accepting
      if (S contains this) {
        start = s
      }
    }

    start
  }

}
