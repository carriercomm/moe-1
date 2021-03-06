package org.moe.runtime.nativeobjects

import org.moe.runtime._

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.util.{Try, Success, Failure}

class MoeArrayObject(
    v: ArrayBuffer[MoeObject],
    t : Option[MoeType] = None
  ) extends MoeNativeObject[ArrayBuffer[MoeObject]](v, t) {

  def this(list: List[MoeObject]) = this(ArrayBuffer(list : _*))

  private def array = getNativeValue

  // Runtime methods
  
  def at_pos (r: MoeRuntime, i: MoeIntObject): MoeObject = {
    if(i.unboxToInt.get >= array.length) r.NativeObjects.getUndef
    else array(i.unboxToInt.get)
  }

  def bind_pos (r: MoeRuntime, i: MoeIntObject, v: MoeObject): MoeObject = {
    val idx = i.unboxToInt.get
    if (idx < array.length)
      array(idx) = v
    else {
      setNativeValue(array.padTo(idx, r.NativeObjects.getUndef))
      array.insert(idx, v)
    }
    v
  }

  def length (r: MoeRuntime): MoeIntObject = r.NativeObjects.getInt(array.length)

  def clear (r: MoeRuntime): MoeObject = {
    array.clear()
    r.NativeObjects.getUndef
  }

  def head (r: MoeRuntime): MoeObject = array.head
  def tail (r: MoeRuntime): MoeObject = r.NativeObjects.getArray( array.tail: _* )

  def shift (r: MoeRuntime): MoeObject =
    if(array.length == 0) r.NativeObjects.getUndef
    else array.remove(0)

  def pop (r: MoeRuntime): MoeObject =
    if(array.length == 0) r.NativeObjects.getUndef
    else array.remove(array.length - 1)

  def unshift (r: MoeRuntime, values: MoeArrayObject): MoeIntObject = {
    array.insertAll(0, values.unboxToArrayBuffer.get)
    r.NativeObjects.getInt(array.length)
  }

  def push (r: MoeRuntime, values: MoeArrayObject): MoeIntObject = {
    array ++= values.unboxToArrayBuffer.get
    r.NativeObjects.getInt(array.length)
  }

  def slice(r: MoeRuntime, indicies: MoeArrayObject): MoeArrayObject = r.NativeObjects.getArray(
    indicies.unboxToArrayBuffer.get.map(i => at_pos(r, i.asInstanceOf[MoeIntObject])) : _*
  )

  def range(r: MoeRuntime, start: MoeIntObject, end: MoeIntObject): MoeArrayObject = r.NativeObjects.getArray(
    array.slice(start.unboxToInt.get, end.unboxToInt.get + 1)
  )

  def reverse(r: MoeRuntime): MoeArrayObject = r.NativeObjects.getArray(array.reverse:_*)

  def join(r: MoeRuntime): MoeStrObject = r.NativeObjects.getStr(array.map(_.unboxToString.get).mkString(""))
  def join(r: MoeRuntime, sep: MoeStrObject): MoeStrObject = r.NativeObjects.getStr(
    array.map(_.unboxToString.get).mkString(sep.unboxToString.get)
  )

  def map (r: MoeRuntime, f: MoeCode): MoeArrayObject = {
    val result = array.map({
      (i) => 
        f.getDeclarationEnvironment.setCurrentTopic(i)
        f.execute(new MoeArguments(List(i)))
    })
    f.getDeclarationEnvironment.clearCurrentTopic
    r.NativeObjects.getArray(result : _*)
  }

  def grep (r: MoeRuntime, f: MoeCode): MoeArrayObject = {
    val result = array.filter({
      (i) => 
        f.getDeclarationEnvironment.setCurrentTopic(i)
        f.execute(new MoeArguments(List(i))).isTrue
    })
    f.getDeclarationEnvironment.clearCurrentTopic
    r.NativeObjects.getArray(result : _*)
  }

  def each (r: MoeRuntime, f: MoeCode): MoeUndefObject = {
    array.foreach({
      (i) => 
        f.getDeclarationEnvironment.setCurrentTopic(i)
        f.execute(new MoeArguments(List(i)))
        ()
    })
    f.getDeclarationEnvironment.clearCurrentTopic
    r.NativeObjects.getUndef
  }

  def first (r: MoeRuntime, f: MoeCode): MoeObject = {
    val result = array.dropWhile({
      (i) => 
        f.getDeclarationEnvironment.setCurrentTopic(i)
        f.execute(new MoeArguments(List(i))).isFalse
    })
    f.getDeclarationEnvironment.clearCurrentTopic
    if (result.isEmpty) r.NativeObjects.getUndef else result.head
  }

  def reduce (r: MoeRuntime, f: MoeCode, init: Option[MoeObject]): MoeObject = init match {
    case Some(init_val) => array.foldLeft(init_val)({ (a, b) => f.execute(new MoeArguments(List(a, b))) })
    case None           => array.reduceLeft        ({ (a, b) => f.execute(new MoeArguments(List(a, b))) })
  }  

  def max (r: MoeRuntime): MoeIntObject = r.NativeObjects.getInt(
      array.map(i => i.unboxToInt.get).max
  )

  def maxstr (r: MoeRuntime): MoeStrObject = r.NativeObjects.getStr(
    array.map(s => s.unboxToString.get).max
  )

  def min (r: MoeRuntime): MoeIntObject = r.NativeObjects.getInt(
      array.map(i => i.unboxToInt.get).min
  )

  def minstr (r: MoeRuntime): MoeStrObject = r.NativeObjects.getStr(
    array.map(s => s.unboxToString.get).min
  )

  def shuffle (r: MoeRuntime): MoeArrayObject = r.NativeObjects.getArray(
    scala.util.Random.shuffle(array) : _*
  )

  def sum (r: MoeRuntime): MoeIntObject = r.NativeObjects.getInt(
    array.map(i => i.unboxToInt.get).sum
  )

  def flatten (r: MoeRuntime): MoeArrayObject = {
    val acc = new ArrayBuffer[MoeObject]
    array.foreach(
      {
        case a: MoeArrayObject => acc ++= a.flatten(r).getNativeValue
        case x: MoeObject      => acc += x
      }
    )
    r.NativeObjects.getArray(acc: _*)
  }

  def repeat (r: MoeRuntime, count: MoeIntObject): MoeArrayObject = {
    val result = new ArrayBuffer[MoeObject]
    for (i <- 1 to count.unboxToInt.get)
      result ++= array
    r.NativeObjects.getArray(result: _*)
  }

  def exists (r: MoeRuntime, item: MoeObject): MoeBoolObject = r.NativeObjects.getBool(
    array.exists( x => x.equal_to(item) )
  )

  // this should preserve the order in the input list
  def uniq (r: MoeRuntime): MoeArrayObject = {
    val uniq_set = array.foldLeft (List[MoeObject]()) { (s, x) => if (s.exists(y => y.equal_to(x))) s else (x :: s) }
    r.NativeObjects.getArray(uniq_set.reverse: _*)
  }

  def zip (r: MoeRuntime, that: MoeArrayObject): MoeArrayObject = {
    val zipped = for ((x, y) <- unboxToArrayBuffer.get zip that.unboxToArrayBuffer.get)
      yield r.NativeObjects.getArray(x, y)
    r.NativeObjects.getArray(zipped: _*)
  }

  def kv (r: MoeRuntime): MoeArrayObject = {
    val indexed = for ((k, v) <- List.range(0, array.length) zip unboxToArrayBuffer.get)
      yield r.NativeObjects.getArray(r.NativeObjects.getInt(k), v)
    r.NativeObjects.getArray(indexed: _*)
  }

  def append (r: MoeRuntime, item: MoeObject) = {
    array += item
    this
  }

  def classify (r: MoeRuntime, mapper: MoeCode): MoeHashObject = {
    val classified = array.foldLeft (new HashMap[String, MoeObject]()) {
      (hm, i) =>
        val key = mapper.execute(new MoeArguments(List(i))).unboxToString.get
        hm += ((key, hm.getOrElse(key, r.NativeObjects.getArray()).asInstanceOf[MoeArrayObject].append(r, i)))
    }
    r.NativeObjects.getHash(classified)
  }

  def categorize (r: MoeRuntime, mapper: MoeCode): MoeHashObject = {
    val categorized = array.foldLeft (new HashMap[String, MoeObject]()) {
      (hm, i) =>
        val categories = mapper.execute(new MoeArguments(List(i))).unboxToArrayBuffer.get
        categories.foreach(
          {
            c =>
              val key = c.unboxToString.get
              hm += ((key, hm.getOrElse(key, r.NativeObjects.getArray()).asInstanceOf[MoeArrayObject].append(r, i)))
          }
        )
        hm
    }
    r.NativeObjects.getHash(categorized)
  }

  def sort (r: MoeRuntime, sorter: Option[MoeCode]): MoeArrayObject = {
    val s = sorter match {
      case Some(code) => (a: MoeObject, b: MoeObject) => code.execute(new MoeArguments(List(a, b))).unboxToInt.get < 0
      case None       => (a: MoeObject, b: MoeObject) => a.unboxToString.get < b.unboxToString.get
    }
    r.NativeObjects.getArray(array sortWith s)
  }

  // equality
  def equal_to (r: MoeRuntime, that: MoeArrayObject): MoeBoolObject = 
    r.NativeObjects.getBool(
      length(r).equal_to(that.length(r))
        &&
      ((unboxToArrayBuffer.get, that.unboxToArrayBuffer.get).zipped.forall( (a, b) => a.equal_to(b) ))
    )

  def not_equal_to (r: MoeRuntime, that: MoeArrayObject): MoeBoolObject =
    r.NativeObjects.getBool(
      length(r).not_equal_to(that.length(r))
        ||
      ((unboxToArrayBuffer.get, that.unboxToArrayBuffer.get).zipped.exists( (a, b) => a.not_equal_to(b) ))
    )

  // MoeNativeObject overrides

  override def copy = new MoeArrayObject(ArrayBuffer(getNativeValue:_*), getAssociatedType)

  // MoeObject overrides
  
  override def isFalse: Boolean = getNativeValue.size == 0
  override def toString: String =
    '[' + getNativeValue.map(_.toString).mkString(", ") + ']'
  
  // unboxing
  
  override def unboxToArrayBuffer: Try[ArrayBuffer[MoeObject]] = Success(getNativeValue)
  override def unboxToInt: Try[Int] = Success(array.length)    
  override def unboxToDouble: Try[Double] = Success(array.length.toDouble)


}
