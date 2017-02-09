import akka.actor._
import scala.math._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.language.postfixOps

//sealed trait GossipMessage
sealed trait GossipMessage
case object StartGossip extends GossipMessage
case object SpreadRumour extends GossipMessage
case object Terminate extends GossipMessage
case object Remind extends GossipMessage
case object FinishGossip extends GossipMessage
case object NoticetoDie extends GossipMessage
case object ShutDown extends GossipMessage
case object NoNeighbour extends GossipMessage
case object PushRemind extends GossipMessage
case class Build(allneighbour: ArrayBuffer[ActorRef]) extends GossipMessage
case class BuildValues(s: Double) extends GossipMessage
case class SetTopo(topo: String) extends GossipMessage
case class Addrandom(ranN: ActorRef) extends GossipMessage
case class CalculatePushSum(s: Double, w: Double) extends GossipMessage
case class CalculatePushAgain(s: Double, w: Double) extends GossipMessage



object Gossip 
{
  def main(args: Array[String]) 
  {
    if (args.length != 3) 
	{
      println("Wrong number of arguments. Please input three arguments")
      
    } else 
	{
      Gossip(numNodes = args(0).toInt, topology = args(1), algorithm = args(2)) 
	}

    def Gossip(numNodes: Int, topology: String, algorithm: String) 
	{
      val system = ActorSystem("GossipSystem")
      println("Building Network Topology...")
      val master = system.actorOf(Props(new GossipMaster(numNodes, topology, algorithm)), name = "master")
      println("Starting protocol")
      master ! StartGossip
    }
  }
}

class GossipMaster(numNodes: Int, topology: String, algorithm: String) extends Actor 
{

   if (numNodes == 0) 
   {
    println("Number of nodes given as input is zero....Shutting down GossipSystem")
    context.system.shutdown()
   }
   
   val edge: Int = ceil(sqrt(numNodes)).toInt
   val ActualnumNodes: Int = if (topology != "2D" && topology != "2DImperfect") numNodes else pow(edge, 2).toInt
   var NodeFinishCount: Int = 0
   var time: Long = 0
   var NetworkActors = new ArrayBuffer[ActorRef]() //Child actors 
   var NoNeighbourCount : Int = 0
   var temp = new ArrayBuffer[ActorRef]()
   var NoticeCount: Int = 0 
   
   //create actors
   for (i <- 0 until ActualnumNodes) 
   { //println("Inside for loop for creation of network actors") 
     NetworkActors += context.actorOf(Props(new GossipWorker(topology)), name = "NetworkActors" + i) 
     //println("Network actor created")  
   }
   println("NetworkActors are created...")
 
   if (algorithm == "pushsum") 
   {  
    println("Building values for s,w for pushsum")
    for (i <- 0 until ActualnumNodes) 
	 { 
      NetworkActors(i) ! BuildValues(i.toDouble)  //Set s
     }
	 println("Values for pushsum are set")
   }
   
   //Build topology
   topology match { 
    case "full" =>
      for (i <- 0 until ActualnumNodes) 
	  {
        NetworkActors(i) ! Build(NetworkActors - NetworkActors(i)) 
      }

	case "line" =>
       
	  println ( "Inside line case for building neighbours")
	    //For first actor in line
	    NetworkActors(0) ! Build(temp += NetworkActors(1)) 
         temp = new ArrayBuffer[ActorRef]()
		
        for (i <- 1 to ActualnumNodes - 2) 
	    {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1)))
          temp = new ArrayBuffer[ActorRef]()		  
        } 
        
         //For last actor in line 		
        NetworkActors(ActualnumNodes - 1) ! Build(temp += NetworkActors(ActualnumNodes - 2))
		 temp = new ArrayBuffer[ActorRef]()
		println("Line network created")
	  
	case "2D" =>
	
	     NetworkActors(0) ! Build(temp += (NetworkActors(1), NetworkActors(edge))) //set neighbor for first element in first line
         temp = new ArrayBuffer[ActorRef]()

      for (i <- 1 to edge - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i + edge))) //first line except first and last
        temp = new ArrayBuffer[ActorRef]()
      }

      NetworkActors(edge - 1) ! Build(temp += (NetworkActors(edge - 2), NetworkActors(edge - 1 + edge))) //last one of first line
       temp = new ArrayBuffer[ActorRef]()

      for (i: Int <- edge to ActualnumNodes - edge - 1) { //Middle lines
        if (i % edge == 0) {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i + 1)))
          temp = new ArrayBuffer[ActorRef]()
        } else if (i % edge == edge - 1) {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1)))
          temp = new ArrayBuffer[ActorRef]()
        } else {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1), NetworkActors(i + 1)))
          temp = new ArrayBuffer[ActorRef]()
        }
      }

      NetworkActors(ActualnumNodes - edge) ! Build(temp += (NetworkActors(ActualnumNodes - edge - edge), NetworkActors(ActualnumNodes - edge + 1))) //set neighbor for first element in last line
      temp = new ArrayBuffer[ActorRef]()

      for (i <- ActualnumNodes - edge + 1 to ActualnumNodes - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i - edge)))  //last line except first and last element
        temp = new ArrayBuffer[ActorRef]()
      }

      NetworkActors(ActualnumNodes - 1) ! Build(temp += (NetworkActors(ActualnumNodes - 2), NetworkActors(ActualnumNodes - 1 - edge))) //last one of last line
      temp = new ArrayBuffer[ActorRef]()
	
	
	
   /* case "2DImperfect" =>
       
       println("Inside imperfect build case")	   
       var RandomList = NetworkActors.clone()
       var RandomNode:ActorRef = null 
       
	   NetworkActors(0) ! Build(temp += (NetworkActors(1), NetworkActors(edge))) //set neighbor for first element in first line
       RandomNode=(RandomList - NetworkActors(0) -- temp)(Random.nextInt((RandomList - NetworkActors(0) -- temp).length))
       NetworkActors(0) ! Addrandom(RandomNode)
       RandomNode ! Addrandom(NetworkActors(0))
       RandomList -= (NetworkActors(0), RandomNode)
       temp = new ArrayBuffer[ActorRef]() 

      for (i <- 1 to edge - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i + edge)))
        if(RandomList.contains(NetworkActors(i))) {
        RandomNode=(RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
        NetworkActors(i) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(i))
        RandomList -= (NetworkActors(i), RandomNode)
        }
		temp = new ArrayBuffer[ActorRef]()
        }

      NetworkActors(edge - 1) ! Build(temp += (NetworkActors(edge - 2), NetworkActors(edge - 1 + edge)))
      if(RandomList.contains(NetworkActors(edge - 1))) {
        RandomNode=(RandomList - NetworkActors(edge - 1) -- temp)(Random.nextInt((RandomList - NetworkActors(edge - 1) -- temp).length))
        NetworkActors(edge - 1) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(edge - 1))
        RandomList -= (NetworkActors(edge - 1), RandomNode)
        }
        temp = new ArrayBuffer[ActorRef]()

      for (i: Int <- edge to ActualnumNodes - edge - 1) { //Middle lines
        if (i % edge == 0) {NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i + 1)))
		if(RandomList.contains(NetworkActors(i)) && RandomList.length >=2) {
          RandomNode=(RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
          NetworkActors(i) ! Addrandom(RandomNode)
          RandomNode ! Addrandom(NetworkActors(i))
          RandomList -= (NetworkActors(i), RandomNode)
          }
		  temp = new ArrayBuffer[ActorRef]()
		}
		
         else if (i % edge == edge - 1) {NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1)))
		 if(RandomList.contains(NetworkActors(i)) && RandomList.length >=2) {
          RandomNode=(RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
          NetworkActors(i) ! Addrandom(RandomNode)
          RandomNode ! Addrandom(NetworkActors(i))
          RandomList -= (NetworkActors(i), RandomNode)
          }
		  temp = new ArrayBuffer[ActorRef]()
		} 
         
		 else {NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1), NetworkActors(i + 1)))
         if(RandomList.contains(NetworkActors(i)) && RandomList.length >=2) {
          RandomNode=(RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
          NetworkActors(i) ! Addrandom(RandomNode)
          RandomNode ! Addrandom(NetworkActors(i))
          RandomList -= (NetworkActors(i), RandomNode)
          }
		  temp = new ArrayBuffer[ActorRef]()
		}
       }

      NetworkActors(ActualnumNodes - edge) ! Build(temp += (NetworkActors(ActualnumNodes - edge - edge), NetworkActors(ActualnumNodes - edge + 1))) //set neighbor for first element in last line
      if(RandomList.contains(NetworkActors(ActualnumNodes - edge))) {
        RandomNode=(RandomList - NetworkActors(ActualnumNodes - edge) -- temp)(Random.nextInt((RandomList - NetworkActors(ActualnumNodes - edge) -- temp).length))
        NetworkActors(ActualnumNodes - edge) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(ActualnumNodes - edge))
        RandomList -= (NetworkActors(ActualnumNodes - edge), RandomNode)
        }
        temp = new ArrayBuffer[ActorRef]()


      for (i <- ActualnumNodes - edge + 1 to ActualnumNodes - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i - edge)))
       if(RandomList.contains(NetworkActors(i))) {
        RandomNode=(RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
        NetworkActors(i) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(i))
        RandomList -= (NetworkActors(i), RandomNode)
        }
        temp = new ArrayBuffer[ActorRef]()		
	}	


      NetworkActors(ActualnumNodes - 1) ! Build(temp += (NetworkActors(ActualnumNodes - 2), NetworkActors(ActualnumNodes - 1 - edge))) //last one of last line
      if(RandomList.contains(NetworkActors(ActualnumNodes - 1))) {
        RandomNode=(RandomList - NetworkActors(ActualnumNodes - 1) -- temp)(Random.nextInt((RandomList - NetworkActors(ActualnumNodes - 1) -- temp).length))
        NetworkActors(ActualnumNodes - 1) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(ActualnumNodes - 1))
        RandomList -= (NetworkActors(ActualnumNodes - 1), RandomNode) 
	 }
	  temp = new ArrayBuffer[ActorRef]()
	 */
	 
	 case "2DImperfect" =>
      
	  println("Inside imperfect build case")
      var RandomList = NetworkActors.clone()
      var RandomNode: ActorRef = null
      NetworkActors(0) ! Build(temp += (NetworkActors(1), NetworkActors(edge))) //Build neighbor for first element in first line

      RandomNode = (RandomList - NetworkActors(0) -- temp)(Random.nextInt((RandomList - NetworkActors(0) -- temp).length))

      NetworkActors(0) ! Addrandom(RandomNode)

      RandomNode ! Addrandom(NetworkActors(0))

      RandomList -= (NetworkActors(0), RandomNode)

      temp = new ArrayBuffer[ActorRef]()

      for (i <- 1 to edge - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i + edge))) //first line except first and last
        if (RandomList.contains(NetworkActors(i))) {
          RandomNode = (RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
          NetworkActors(i) ! Addrandom(RandomNode)
          RandomNode ! Addrandom(NetworkActors(i))
          RandomList -= (NetworkActors(i), RandomNode)
        }
        temp = new ArrayBuffer[ActorRef]()
      }

      NetworkActors(edge - 1) ! Build(temp += (NetworkActors(edge - 2), NetworkActors(edge - 1 + edge))) //last one of first line
      if (RandomList.contains(NetworkActors(edge - 1))) {
        RandomNode = (RandomList - NetworkActors(edge - 1) -- temp)(Random.nextInt((RandomList - NetworkActors(edge - 1) -- temp).length))
        NetworkActors(edge - 1) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(edge - 1))
        RandomList -= (NetworkActors(edge - 1), RandomNode)
      }
      temp = new ArrayBuffer[ActorRef]()
       
	  println("Inside imperfect build case--first line") 
      for (i: Int <- edge to ActualnumNodes - edge - 1) { //Middle lines
        if (i % edge == 0) {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i + 1)))
          if (RandomList.contains(NetworkActors(i)) && RandomList.length >= 2) {
            RandomNode = (RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
            NetworkActors(i) ! Addrandom(RandomNode)
            RandomNode ! Addrandom(NetworkActors(i))
            RandomList -= (NetworkActors(i), RandomNode)
          }
          temp = new ArrayBuffer[ActorRef]()
        } else if (i % edge == edge - 1) {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1)))
          if (RandomList.contains(NetworkActors(i)) && RandomList.length >= 2) {
            RandomNode = (RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
            NetworkActors(i) ! Addrandom(RandomNode)
            RandomNode ! Addrandom(NetworkActors(i))
            RandomList -= (NetworkActors(i), RandomNode)
          }
          temp = new ArrayBuffer[ActorRef]()
        } else {
          NetworkActors(i) ! Build(temp += (NetworkActors(i - edge), NetworkActors(i + edge), NetworkActors(i - 1), NetworkActors(i + 1)))
          if (RandomList.contains(NetworkActors(i)) && RandomList.length >= 2) {
            RandomNode = (RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
            NetworkActors(i) ! Addrandom(RandomNode)
            RandomNode ! Addrandom(NetworkActors(i))
            RandomList -= (NetworkActors(i), RandomNode)
          }
          temp = new ArrayBuffer[ActorRef]()
        }
      }
      println("Inside imperfect build case...middle lines")
	  
      NetworkActors(ActualnumNodes - edge) ! Build(temp += (NetworkActors(ActualnumNodes - edge - edge), NetworkActors(ActualnumNodes - edge + 1))) //Build neighbor for first element in last line
      if (RandomList.contains(NetworkActors(ActualnumNodes - edge)) && RandomList.length >= 2) {
        RandomNode = (RandomList - NetworkActors(ActualnumNodes - edge) -- temp)(Random.nextInt((RandomList - NetworkActors(ActualnumNodes - edge) -- temp).length))
        NetworkActors(ActualnumNodes - edge) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(ActualnumNodes - edge))
        RandomList -= (NetworkActors(ActualnumNodes - edge), RandomNode)
      }
      temp = new ArrayBuffer[ActorRef]()

      for (i <- ActualnumNodes - edge + 1 to ActualnumNodes - 2) {
        NetworkActors(i) ! Build(temp += (NetworkActors(i - 1), NetworkActors(i + 1), NetworkActors(i - edge))) //last line except first and last element
        if (RandomList.contains(NetworkActors(i)) && RandomList.length >= 2) {
          RandomNode = (RandomList - NetworkActors(i) -- temp)(Random.nextInt((RandomList - NetworkActors(i) -- temp).length))
          NetworkActors(i) ! Addrandom(RandomNode)
          RandomNode ! Addrandom(NetworkActors(i))
          RandomList -= (NetworkActors(i), RandomNode)
        }
        temp = new ArrayBuffer[ActorRef]()
      }

      NetworkActors(ActualnumNodes - 1) ! Build(temp += (NetworkActors(ActualnumNodes - 2), NetworkActors(ActualnumNodes - 1 - edge))) //last one of last line
      if (RandomList.contains(NetworkActors(ActualnumNodes - 1)) && RandomList.length >= 2) {
        RandomNode = (RandomList - NetworkActors(ActualnumNodes - 1) -- temp)(Random.nextInt((RandomList - NetworkActors(ActualnumNodes - 1) -- temp).length))
        NetworkActors(ActualnumNodes - 1) ! Addrandom(RandomNode)
        RandomNode ! Addrandom(NetworkActors(ActualnumNodes - 1))
        RandomList -= (NetworkActors(ActualnumNodes - 1), RandomNode)
      }
      temp = new ArrayBuffer[ActorRef]()
	  println("Inside imperfect build case...last line....network built")
	
	}
        	  
   def receive = 
   {
      case StartGossip =>
		println("Starting measurement of time...") 
		time = System.currentTimeMillis()
		if (algorithm == "gossip") 
		{
			NetworkActors(Random.nextInt(ActualnumNodes)) ! SpreadRumour
		} else if (algorithm == "pushsum") 
	    {   println("algorithm received is pushsum")
			NetworkActors(Random.nextInt(ActualnumNodes)) ! CalculatePushSum(0,0)
		} else 
		{
			println("Wrong algorithm given as input. Please input gossip or pushsum")
			context.system.shutdown()
		}
		
	  case NoticetoDie =>
	    NoticeCount +=1
		//println("noticecount:"+ NoticeCount)
        if (NoticeCount == numNodes)
		{  
		   println("All actors terminated...shutting down system")
		   println("Convergence Time: " + (System.currentTimeMillis() - time) + " milliseconds")
           context.system.shutdown()		
        }  
	  case FinishGossip =>
        NodeFinishCount += 1
		//println("nodefinishcount :" + NodeFinishCount )
        if (NodeFinishCount == ActualnumNodes) //for gossip
	    { 
		  //context.system.shutdown()
		  println("All given nodes have received message " + "Convergence Time: " + (System.currentTimeMillis() - time) + " milliseconds")
        }
		
		/*if (algorithm == "gossip" )
		{    context.system.shutdown() 
		     println("All actors terminated...shutting down system")
		}
        */
		
        if (algorithm == "pushsum" && NodeFinishCount == 1) 
	    {
           context.system.shutdown()
          //println("Shutting down system....One node terminated as convergence ratio is reached for push sum")
          println("Convergence Time: " + (System.currentTimeMillis() - time) + " milliseconds")
        }
		
	  case NoNeighbour =>
           NoNeighbourCount += 1	  
    }
	
}


class GossipWorker(topology: String) extends Actor {
  import context._

  var neighbour = new ArrayBuffer[ActorRef]()
  var rumorCount: Int = 0
  var localmaster: ActorRef = null
  var isDone: Boolean = false
  var mys: Double = 1
  var myw: Double = 1
  var lastvalue: Double = 0
  var currvalue: Double = 0
  var valueCount: Int = 0
  var finished: Boolean = false
  var reached: Boolean = false
  var shutdownCount: Int = 0

  def receive = {

    case CalculatePushSum(s, w) =>
      if (!isDone) 
	  { //println(" Pushsum case... ") 
        mys = (s + mys) / 2
        myw = (w + myw) / 2
        currvalue = mys / myw
		//println("Currvalue is :" + currvalue)
		
        if (abs(currvalue - lastvalue) <=  0.0000000001 && w != 0) //1e-10 
		{ 
          valueCount += 1
		  //println("Valuecount is :" + valueCount)
        } 
		else 
		{
          valueCount = 0
		  //println("Valuecount is :" + valueCount)
        }
        lastvalue = currvalue
		
        if (valueCount < 3 && neighbour.length > 0) 
		{  //println ("Valuecount is less than 3....more calculation") 
          neighbour(Random.nextInt(neighbour.length)) ! CalculatePushSum(mys, myw)
		  if (!reached) {
            self ! CalculatePushAgain
            reached = true
          }
        }
        else 
		{  println("Value did not change for 3 times")
          isDone = true
          for (x: ActorRef <- neighbour)
		  {
            x ! Terminate
          }
		  if (!finished) 
		  {
            localmaster ! FinishGossip
            finished = true
          }
        }
      }
	  
    case CalculatePushAgain =>
      mys /= 2
      myw /= 2
	  //println("inside pushsum again case")
      //neighbor(Random.nextInt(neighbor.length)) ! Push(mys, myw)
      context.system.scheduler.scheduleOnce(1000 milliseconds, neighbour(Random.nextInt(neighbour.length)), CalculatePushSum(mys, myw))
      //self ! Push(0, 0)
      context.system.scheduler.scheduleOnce(1000 milliseconds, self, CalculatePushAgain)
    
	
    case SpreadRumour =>
	   
      if (!isDone) 
	  { // println("Spreading rumour case...")
        if (!reached) 
		{
          if (!finished) 
		  { //println(" Gossip spread to the node...")
            //localmaster ! CheckConvergence
			 localmaster ! FinishGossip
            finished = true
          }
          reached = true
          self ! Remind
        }
        
        rumorCount += 1
		
        if (rumorCount < 10 && neighbour.length > 0) 
		{
		 //println("Count did not reach 10....spreading more....")
         //neighbour(Random.nextInt(neighbour.length)) ! SpreadRumour   
        } else 
		{ 
		  //println(" proceeding to terminate"+"rumorCount:"+rumorCount)
          isDone = true
          //for (x: ActorRef <- neighbour)
            //{self ! Terminate }
			neighbour -= self
			context.stop(self)
			localmaster ! NoticetoDie
			//println("Actor stopped and notice sent")
			
	    }
      }

   case Remind =>
      if (rumorCount < 10 && neighbour.length > 0 && !isDone) 
	  { //println("Count did not reach 10....spreading more....")
        neighbour(Random.nextInt(neighbour.length)) ! SpreadRumour
        topology match 
		{
          case "line" =>
		    //println("inside line case of remind...")
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
          case "2D" =>
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
          case _ =>
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
        }		
      }
	  
	case Terminate =>
        if (!isDone) {
        neighbour -= sender
		//context.stop(self)
		//println("Stopped self actor")
		//localmaster ! NoticetoDie
        if (neighbour.length <= 0) {
          isDone = true
          if (!finished) {
            localmaster ! NoNeighbour
            localmaster ! FinishGossip
            finished = true
          }
          //context.stop(self)
        }
      }	
    
	/*
    case Terminate =>
      if (!isDone) 
	  {
        neighbour -= sender
		println("Neighbour length :" + neighbour.length)
		context.stop(self)
		println("Actor stopped")
        if (neighbour.length <= 0) 
		{
          isDone = true
		  println("interior loop - 1 of terminate")
		  localmaster ! FinishGossip 
		  context.stop(self)
          if (!finished) 
		  { println("interior loop of terminate")
            localmaster ! NoNeighbour
            localmaster ! FinishGossip
            finished = true
          } 
        }
      }
      */
    case Build(allneighbours) =>
      neighbour ++= allneighbours
      localmaster = sender

    case BuildValues(s) =>
      mys = s
      lastvalue = mys / myw

    case Addrandom(randomN) =>
      neighbour += randomN

    case ShutDown =>
      context.stop(self)
	  
  }
  
}	
     