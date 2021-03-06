package org.moe.interpreter.guts

import org.moe.interpreter._
import org.moe.runtime._
import org.moe.runtime.nativeobjects._
import org.moe.ast._

object Packages extends Utils {

  def declaration (i: MoeInterpreter, r: MoeRuntime): PartialFunction[(MoeEnvironment, AST), MoeObject] = {
    case (env, PackageDeclarationNode(name, body, version, authority)) => {
      val newEnv = new MoeEnvironment(Some(env))
      val parent = getCurrentPackage(env)
      val pkgs   = MoePackage.createPackageTreeFromName(name, version, authority, newEnv, parent)
      // attach the root
      parent.addSubPackage(pkgs._1) 
      // make the leaf the current package 
      newEnv.setCurrentPackage(pkgs._2) 

      val result = i.compile_and_evaluate(newEnv, body)
      
      // return the root
      pkgs._1
    }
  }
}