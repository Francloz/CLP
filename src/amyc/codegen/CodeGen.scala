package amyc
package codegen

import analyzer._
import ast.Identifier
import ast.SymbolicTreeModule.{Call => AmyCall, Div => AmyDiv, And => AmyAnd, Or => AmyOr, _}
import utils.{Context, Pipeline}
import wasm._
import Instructions._
import Utils._

// Generates WebAssembly code for an Amy program
object CodeGen extends Pipeline[(Program, SymbolTable), Module] {
  

  def run(ctx: Context)(v: (Program, SymbolTable)): Module = {
    val (program, table) = v
    
    // Show the message str
    def reportStr(str : String) : Code = {
      mkString(str) <:> Call("Std_printString") <:> Drop 
    }

    // Show the local i
    def reportVal(i : Int) : Code = {
      GetLocal(i) <:> reportInt
    }

    // Show the integer on the stack
    def reportInt : Code = {
      Call("Std_digitToString") <:> Call("Std_printString") <:> Drop 
    }

    //  Shows the memory state
    def report : Code  = {
      mkString("Memory ") <:> 
      Const(0)  <:> Load <:> Call("Std_digitToString") <:> Call("String_concat") <:> mkString(" ") <:> Call("String_concat") <:> 
      Const(4)  <:> Load <:> Call("Std_digitToString") <:> Call("String_concat") <:> mkString(" ") <:> Call("String_concat") <:>
      Const(8)  <:> Load <:> Call("Std_digitToString") <:> Call("String_concat") <:> mkString(" ") <:> Call("String_concat") <:>
      Const(12) <:> Load <:> Call("Std_digitToString") <:> Call("String_concat") <:> mkString(" ") <:> Call("String_concat") <:> 
      Const(16) <:> Load <:> Call("Std_digitToString") <:> Call("String_concat") <:> mkString(" ") <:> Call("String_concat") <:>
      mkString("\nLocals ") <:> Call("String_concat") <:>
      GetLocal(0) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(1) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(2) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(3) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(4) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(5) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(6) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(7) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(8) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      GetLocal(9) <:> Call("Std_digitToString") <:> mkString(" ") <:> Call("String_concat") <:> Call("String_concat") <:> 
      Call("Std_printString") <:> Drop  
    }

    // Generate code for an Amy module
    def cgModule(moduleDef: ModuleDef): List[Function] = {
      val ModuleDef(name, defs, optExpr) = moduleDef
      // Generate code for all functions
      defs.collect { case fd: FunDef if !builtInFunctions(fullName(name, fd.name)) =>
        cgFunction(fd, name, false)
      } ++
      // Generate code for the "main" function, which contains the module expression
      optExpr.toList.map { expr =>
        val mainFd = FunDef(Identifier.fresh("main"), Nil, TypeTree(IntType), expr)
        cgFunction(mainFd, name, true)
      }
    }

    // Generate code for a function in module 'owner'
    def cgFunction(fd: FunDef, owner: Identifier, isMain: Boolean): Function = {
      // Note: We create the wasm function name from a combination of
      // module and function name, since we put everything in the same wasm module.
      val name = fullName(owner, fd.name)
      Function(name, fd.params.size, isMain){ lh =>
        val locals = fd.paramNames.zipWithIndex.toMap
        val body = cgExpr(fd.body)(locals, lh)
        if (isMain) {
          body <:> Drop // Main functions do not return a value,
                        // so we need to drop the value generated by their body
        } else {
          body
        }
      }
    }

    // Generate code for an expression expr.
    // Additional arguments are a mapping from identifiers (parameters and variables) to
    // their index in the wasm local variables, and a LocalsHandler which will generate
    // fresh local slots as required.
    def cgExpr(expr: Expr)(implicit locals: Map[Identifier, Int], lh: LocalsHandler): Code = {
      expr match {
        // Variables
        case  Variable(name)    =>  GetLocal(locals(name))
        
        // Literals
        case  IntLiteral(value)     => Const(value)
        case  BooleanLiteral(value) => if (value == false) Const(0) else Const(1)
        case  StringLiteral(value)  => mkString(value)
        case  UnitLiteral()         => Const(0)
    
        // // Binary integer operators
        case Plus(lhs, rhs)       => cgExpr(lhs) <:> cgExpr(rhs) <:> Add
        case Minus(lhs, rhs)      => cgExpr(lhs) <:> cgExpr(rhs) <:> Sub
        case Times(lhs, rhs)      => cgExpr(lhs) <:> cgExpr(rhs) <:> Mul
        case Mod(lhs, rhs)        => cgExpr(lhs) <:> cgExpr(rhs) <:> Rem
        case LessThan(lhs, rhs)   => cgExpr(lhs) <:> cgExpr(rhs) <:> Lt_s
        case LessEquals(lhs, rhs) => cgExpr(lhs) <:> cgExpr(rhs) <:> Le_s
        case Equals(lhs, rhs)     => cgExpr(lhs) <:> cgExpr(rhs) <:> Eq

        case AmyDiv(lhs, rhs)        => cgExpr(lhs) <:> cgExpr(rhs) <:> Div
        case AmyAnd(lhs, rhs)        => cgExpr(lhs) <:> cgExpr(rhs) <:> And
        case AmyOr(lhs, rhs)         => cgExpr(lhs) <:> cgExpr(rhs) <:> Or

        // Binary string operation
        case Concat(lhs, rhs)     => cgExpr(lhs) <:> cgExpr(rhs) <:> Call("String_concat")

        // Unary operators
        case Not(e) => Const(0)  <:> cgExpr(e) <:> Sub
        case Neg(e) => cgExpr(e) <:> Eqz

        // The ; operator
        case Sequence(e1, e2) => cgExpr(e1) <:> Drop <:> cgExpr(e2)
        
        // Local variable definition
        case Let(df, value, body) => 
          val ptr = lh.getFreshLocal();
          cgExpr(value) <:>  SetLocal(ptr) <:>  cgExpr(body)(locals + (df.name -> ptr), lh)
        
        // If-then-else
        case Ite(cond, thenn, elze) => 
          cgExpr(cond) <:> If_i32 <:> cgExpr(thenn) <:> Else <:> cgExpr(elze) <:> End

        // Represents a computational error; prints its message, then exits
        case Error(msg) => 
          cgExpr(msg) <:> Call("Std_printString") <:> Unreachable  
          
        // Match expression
        case Match(scrut, cases) => 
          //End of the match is called EndMatch
          //End of the pattern is called EndPat
          def attemtAssignArguments(pats : List[Pattern], cmpIdx : Int): (Map[Identifier,Int], Code) = {
            pats match {
              case x :: xs => xs match {
                case y :: ys =>  
                  val (asig, code) = attempAsignation(x, cmpIdx)
                  val (restAsig, restCode) = attemtAssignArguments(xs, cmpIdx)
                  (restAsig ++ asig, code <:> restCode)
                case y => attempAsignation(x, cmpIdx)
              }
              case x => sys.error("Invalid parameter. Empty list give.")
            }
          }
          // Loads fields from address ptr+4*i to address ptr+4*n
          def loadFields(ptr : Int, i : Int, n : Int) : Code = {
            if (i <= n) {
              val load = GetLocal(ptr) <:> Const(i) <:> Const(4) <:> Mul <:> Add <:> Load
              if(i < n) loadFields(ptr, i+1, n) <:> load else load
            }else{
              sys.error("No fields given.")
            }
          }
          // Creates the code of a pattern matching. Stores whether the match is successful in Locals(cmpIdx)
          def attempAsignation(pat : Pattern, cmpIdx : Int) : (Map[Identifier, Int], Code) ={
            val cmp_code = GetLocal(cmpIdx) <:> And <:> SetLocal(cmpIdx)
            pat match {
              // Wildcard, ignore the argument
              case WildcardPattern() => (Map[Identifier, Int](), Drop)
              // Literal, compare and update Local(cmpIdx)
              case LiteralPattern(lit) => (Map[Identifier, Int](), cgExpr(lit) <:> Eq <:> cmp_code)
              // Identifier, add to locals
              case IdPattern(id) => 
                val idx = lh.getFreshLocal()
                (Map[Identifier, Int](id -> idx), SetLocal(idx) <:> reportStr("Stored.") <:> report)
              // CaseClass, compare contructor index and subpatterns
              case CaseClassPattern(constr, pats) =>
                table.getConstructor(constr) getOrElse sys.error("Constructor not found") match { 
                  case ConstrSig(_, parent, index) => 
                    // Store the pointer to the object (for field load)
                    val ptr = lh.getFreshLocal()
                    // Comparison with the constructor index
                    val checkCostr = GetLocal(ptr) <:> Load <:> Const(index) <:> Eq <:> cmp_code 
                    // if there are no fields
                    if (pats.isEmpty)
                      (Map[Identifier, Int](), checkCostr)
                    else // If there are fields
                    {
                      // Attempst to match the arguments
                      val (moreLocals, code) = attemtAssignArguments(pats, cmpIdx)
                      // Returns the result adding the constructor index check
                      (moreLocals,  SetLocal(ptr) <:> loadFields(ptr, 1, pats.length) <:> code <:> checkCostr) // <:> reportStr("After Constr value: ") <:> reportVal(cmpIdx)
                    }
                }
                
            }
          }
          // Creates the code of a matchcase
          def codeMatchCase(indexSaved: Int, pat : MatchCase, indexResult : Int) : Code = {
            pat match {
              case MatchCase(a, b) => 
                // Index to store the pattern matching result (Boolean)
                val cmpIdx = lh.getFreshLocal()
                val (moreLocals, code) = attempAsignation(a, cmpIdx)
                val extraIdx = lh.getFreshLocal()
                val resultCode : Code = cgExpr(b)(locals  ++ moreLocals, lh)
                // Initialize the match
                Block("EndPat") <:> GetLocal(indexSaved) <:> Const(1) <:> SetLocal(cmpIdx) <:> 
                // Execute the match
                code <:> 
                // Get the result of the match
                GetLocal(cmpIdx) <:>
                // If un-successful jumpto next match case
                If_void <:> Else <:> Br("EndPat") <:> End <:> 
                // Execute the expresion
                resultCode <:> SetLocal(indexResult) <:> Br("EndMatch") <:> End
            }
          }
          // Concatenate all the match expresions (With the error at the end as default case)
          def codeMatches(indexSaved: Int, cases : List[MatchCase], indexResult : Int) : Code = {
            cases match { 
              case x :: xs => codeMatchCase(indexSaved, x, indexResult) <:> codeMatches(indexSaved, xs, indexResult)
              case nil => mkString("Match error") <:> Call("Std_printString") <:> Unreachable 
            }
          }
          // Code ofthe scrut
          val codeScrut = cgExpr(scrut)
          // Where we have saved the scrut
          val indexSaved = lh.getFreshLocal()
          // Where the resultwill be saved
          val indexResult = lh.getFreshLocal()
          // Match cases
          val codeMatches_ = codeMatches(indexSaved, cases, indexResult)
          
          codeScrut <:> SetLocal(indexSaved) <:> Block("EndMatch") <:> codeMatches_ <:> End <:> GetLocal(indexResult)

        // Function/constructor call
        case AmyCall(id, args) => 
          // Evaluates the arguments in opposite order
          def concatArgs(args : List[Expr]) : Code = {
            args match {
              case x :: y :: xs => concatArgs(y :: xs) <:> cgExpr(x) 
              case x :: Nil => cgExpr(x) 
              case Nil => sys.error("No arguments given")
            }
          }
          // Performs n consecutive allocations
          def storeFields(n : Int) : Code = {
            if (n > 0) {
              val incrMem = GetGlobal(memoryBoundary) <:> Const(4) <:> Add <:> SetGlobal(memoryBoundary)
              val loc = lh.getFreshLocal()
              val storeField =  SetLocal(loc) <:> GetGlobal(memoryBoundary) <:> GetLocal(loc) <:> Store <:> incrMem
              if(n > 1) storeField <:> storeFields(n-1) else storeField
            }else{
              sys.error("No fields given.")
            }
          }


          val funSig = table.getFunction(id) getOrElse (table.getConstructor(id) getOrElse sys.error("Non existent call identifier"))

          funSig match {
            case FunSig(_, _, owner) =>
              // Call function
              val call =  Call(s"${owner}_${id.toString}")
              if (args.isEmpty) call else concatArgs(args) <:> call
            case ConstrSig(_, parent, index) =>
              val iniConstructor = 
              // Get pointer that will be returned
              
              // Store the constructor index
              GetGlobal(memoryBoundary) <:> Const(index) <:> Store <:> GetGlobal(memoryBoundary) <:> Const(4) <:> Add <:> SetGlobal(memoryBoundary)
              // Store the fields (Optional)
              if (args.isEmpty)
                 GetGlobal(memoryBoundary) <:> iniConstructor 
              else {
                val argCode = concatArgs(args)
                val ptr = lh.getFreshLocal()
                argCode <:> GetGlobal(memoryBoundary) <:> SetLocal(ptr) <:> iniConstructor <:> storeFields(args.length) <:> GetLocal(ptr)
              }
          }
          
      }
    }
    
    println(program.modules flatMap cgModule);

    Module(
      program.modules.last.name.name,
      defaultImports,
      globalsNo,
      wasmFunctions ++ (program.modules flatMap cgModule)
    )

  }
}
