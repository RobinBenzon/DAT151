import CPP.Absyn.*;
import java.util.HashMap;
import java.util.LinkedList;  

public class TypeChecker {
    public static enum TypeCode 
    {
        Type_int , Type_double , Type_bool , Type_void 
    }
    public static class FunType {
        public LinkedList<TypeCode> args ; 
        public TypeCode returnType; 
    }
    public static class Env {
        public HashMap<String , FunType> signature ; 
        public LinkedList<HashMap<String , TypeCode>> contexts ; 
        public int returnFlag ; 
        public Env() 
        {
            contexts = new LinkedList<HashMap<String , TypeCode>>();
            signature = new HashMap<String , FunType> () ; 
            returnFlag = 0 ; 
            enterScope(); 
        }
        public TypeCode lookupVar(String id )
        {
            // check all the scopes 
            for (HashMap<String , TypeCode> context : contexts)
            {
                TypeCode t = context.get(id) ; 
                if(t != null)
                    return t ; 
            }
            throw new TypeException("There is no variable called [ " + id + "] ");
        }
        public boolean isFunDecl (String id )
        {
            return signature.containsKey(id); 
        }
        public boolean isVarDecl (String id )
        {
            // just check if the current scope contains the variable
            TypeCode t ; 
            HashMap<String , TypeCode> context =  contexts.getFirst(); 
            t = context.get(id);
            if(t != null)
                return true ; 
            return false ;
        }
        public FunType lookupFun (String id)
        {
            FunType t = signature.get(id) ; 
            if (t == null)
                throw new TypeException("There is no [" + id + "] function");
            else
                return t ; 
        }
        public void addVar(String id , TypeCode Ty )
        {
            //add var to current scope
            HashMap<String , TypeCode> cur_scope = contexts.getFirst() ; 
            if(isVarDecl(id))
            {
                throw new TypeException("The variable [ " + id + "] has been decalared" );
            }
            else
            {
                cur_scope.put(id , Ty );
            }
        }
        public void enterScope()
        {
            contexts.addFirst(new HashMap<String , TypeCode>());
        }
        public void leaveScope()
        {
            contexts.removeFirst(); 
        }
    }
    // entry point of type checker 
    public void typecheck(Program p) 
    {
        PDefs defs = (PDefs) p ; 
        Env env = new Env() ; 
        // add build-in function def 
        FunType readInt = new FunType(); 
        FunType readDouble = new FunType(); 
        FunType printInt = new FunType(); 
        FunType printDouble = new FunType(); 

        readInt.returnType = TypeCode.Type_int ; 
        readInt.args = new LinkedList<TypeCode>() ;
        env.signature.put("readInt", readInt) ;

        readDouble.returnType = TypeCode.Type_double ;
        readDouble.args = new LinkedList<TypeCode>() ;
        env.signature.put("readDouble", readDouble) ;

        printInt.returnType = TypeCode.Type_void ;
        printInt.args = new LinkedList<TypeCode>() ;
        printInt.args.addLast(TypeCode.Type_int);
        env.signature.put("printInt", printInt) ;

        printDouble.returnType = TypeCode.Type_void ;
        printDouble.args = new LinkedList<TypeCode>() ;
        printDouble.args.addLast(TypeCode.Type_double);
        env.signature.put("printDouble", printDouble) ;

        for(Def d : defs.listdef_ )
        {
            // add function declaration 
            checkDef1(d , env) ; 
        }
        for(Def d : defs.listdef_)
        {
            // check statement in each function 
            checkDef2(d , env);
            if(env.returnFlag == 0) 
                throw new TypeException("Missing return Stattment");
            else
                env.returnFlag = 0 ; 
        }
    }
    // check for Defs 
    private void checkDef1(Def d , Env env)
    {
        d.accept(new defAdder() , env);
    }
    private void checkDef2(Def d , Env env)
    {
        d.accept(new defFuncStmChecker() , env);
    }
    private class defAdder implements Def.Visitor<Object , Env> 
    {
        public Object visit(CPP.Absyn.DFun p, Env env)
        {
            /* Code For DFun Goes Here */
            if(env.isFunDecl(p.id_))
                throw new TypeException("the function identifier has been used!");
            
            FunType FT = new FunType() ; 
            FT.returnType = getTypeCode(p.type_); 
            FT.args = new LinkedList<TypeCode>() ; 
            for (Arg x : p.listarg_) 
            {
                FT.args.addLast(getTypeCode(((ADecl)x).type_)) ; 
            }
            env.signature.put(p.id_ , FT) ; 
            return null;
        }                                
    }
    private class defFuncStmChecker implements Def.Visitor<Object , Env>
    {
        public Object visit(CPP.Absyn.DFun p , Env env)
        {
            env.enterScope() ; 
            // add function args to current scope 
            ADecl tmp ;
            for (Arg x : p.listarg_)
            {  
                tmp = (ADecl)x ; 
                env.addVar(tmp.id_ ,getTypeCode( tmp.type_));
            }
            // add a var call return to context to check SReturn's Type
            // because return is reserved , no variable has the name "return"
            TypeCode returnType = getTypeCode(p.type_);
            env.addVar("return" , returnType) ; 
            // void and main function do not neccesarily have return statement 
            if(returnType == TypeCode.Type_void || p.id_ == "main")
                env.returnFlag = 1 ;
            for (Stm x : p.liststm_)
            {
                checkStm(x , env); 
            }
            env.leaveScope();
            return null; 
        }
    }
    // check for statements 
    private void checkStm (Stm x , Env env)
    {
        x.accept(new StmChecker() , env);
    }
    private class StmChecker implements Stm.Visitor<Object , Env>
    {
        public Object visit(CPP.Absyn.SExp p, Env env)
        {
            /* Code For SExp Goes Here */
            TypeCode expType = checkExp(p.exp_ , env );
            return null;
        }
        public Object visit(CPP.Absyn.SDecls p, Env env)
        {
            /* Code For SDecls Goes Here */
            TypeCode Ty = getTypeCode(p.type_);
            for (String x : p.listid_) {
                env.addVar(x , Ty );
            }
            return null;
        }
        public Object visit(CPP.Absyn.SInit p, Env env)
        {
            /* Code For SInit Goes Here */
            // check if the iden. of the variable exists in current scope  
            if(env.isVarDecl(p.id_))
                throw new TypeException("SInit error: variable name confict");
            // check if the type of the exp match the type of the decl.
            TypeCode expType = checkExp(p.exp_ , env );
            TypeCode VarType = getTypeCode(p.type_); 
            if(expType != VarType)
                throw new TypeException("SInit error: Types do not match");
            else
                env.addVar(p.id_ , VarType);
            return null;
        }
        public Object visit(CPP.Absyn.SReturn p, Env env)
        {
            /* Code For SReturn Goes Here */
             // check the returnType equal to function declaration 
            TypeCode expType = checkExp(p.exp_ , env );
            TypeCode returnType  = env.lookupVar("return");
            if(expType != returnType )
                throw new TypeException("SReturn error: return type not match");
            env.returnFlag = 1 ; 
            return null;
        }
        public Object visit(CPP.Absyn.SWhile p, Env env)
        {
            /* Code For SWhile Goes Here */
            // check the exp return a boolean 
            TypeCode expType = checkExp(p.exp_ , env );
            if(expType != TypeCode.Type_bool)
                throw new TypeException("SWhile error: exp not boolean type");
            // check the stm of while 
            checkStm(p.stm_ , env) ; 
            return null;
        }
        public Object visit(CPP.Absyn.SBlock p, Env env)
        {
            /* Code For SBlock Goes Here */
            env.enterScope();
            for (Stm x : p.liststm_) {
               checkStm(x , env);
            } 
            env.leaveScope() ;
            return null;
        }
        public Object visit(CPP.Absyn.SIfElse p, Env env)
        {
            /* Code For SIfElse Goes Here */
            TypeCode expType = checkExp(p.exp_ , env );
            if(expType != TypeCode.Type_bool)
                throw new TypeException("SIfElse error: exp not booelan type ");
            checkStm(p.stm_1 , env); 
            checkStm(p.stm_2 , env);
            return null;
        }
          
    }
    // check expressions
    public TypeCode checkExp(Exp exp , Env env)
    {
        return exp.accept(new ExpChecker() , env );
    };
    private class ExpChecker implements Exp.Visitor<TypeCode , Env>   
    {
        public TypeCode visit(CPP.Absyn.ETrue p , Env env) 
        {
            //System.out.println ("ETrue");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EFalse p , Env env)
        {
            //System.out.println ("EFalse");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EInt p , Env env)
        {
            //System.out.println ("EInt");
            return TypeCode.Type_int ;
        }
        public TypeCode visit(CPP.Absyn.EApp p , Env env)
        {
            //System.out.println ("EApp");
            // return return type the function
            FunType fun = env.lookupFun(p.id_); 
            int tmp = 0 , arg_list1 = fun.args.size() , arg_list2 = p.listexp_.size(); 
            if(arg_list1 != arg_list2)
                throw new TypeException("EApp error: lenth of args not match");
            for(Exp x : p.listexp_)
            {
                if(checkExp(x, env) != fun.args.get(tmp) )
                    throw new TypeException("EApp error: "+ tmp + " th arg type not match");
                else
                    tmp ++;
            }
            return fun.returnType ;
        }
        public TypeCode visit(CPP.Absyn.EDouble p , Env env)
        {
            //System.out.println ("EDouble");
            return TypeCode.Type_double;
        }
        public TypeCode visit(CPP.Absyn.EId p , Env env)
        {
            //System.out.println ("EId");
            return env.lookupVar(p.id_) ;
        }
        public TypeCode visit(CPP.Absyn.EPostIncr p , Env env)
        {
            TypeCode expType = checkExp(p.exp_ , env);
            if(expType == TypeCode.Type_bool)
                throw new TypeException("EPostIncr error : can't use boolean");
            //System.out.println ("EPostIncr");
            return expType ;
        }
        public TypeCode visit(CPP.Absyn.EPostDecr p , Env env)
        {
            //System.out.println ("EPostDecr");
            TypeCode expType = checkExp(p.exp_ , env);
            if(expType == TypeCode.Type_bool)
                throw new TypeException("EPostIncr error : can't use boolean");
            return expType ;
        }
        public TypeCode visit(CPP.Absyn.EPreIncr p , Env env)
        {
            //System.out.println ("EPreIncr");
            TypeCode expType = checkExp(p.exp_ , env);
            if(expType == TypeCode.Type_bool)
                throw new TypeException("EPostIncr error : can't use boolean");
            return expType ;
        }
        public TypeCode visit(CPP.Absyn.EPreDecr p , Env env)
        {
            //System.out.println ("EPreDecr");
            TypeCode expType = checkExp(p.exp_ , env);
            if(expType == TypeCode.Type_bool)
                throw new TypeException("EPostIncr error : can't use boolean");
            return expType ;
        }
        public TypeCode visit(CPP.Absyn.ETimes p , Env env)
        {
           // System.out.println ("ETimes");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("ETimes error: exps not match");
            else if (t1 == TypeCode.Type_bool || t2 == TypeCode.Type_bool)
                throw new TypeException("ETimes error: exp is boolean");
            else
                return t1 ;
        }
        public TypeCode visit(CPP.Absyn.EDiv p , Env env)
        {
           // System.out.println ("EDiv");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EDiv error: exps not match");
            else if (t1 == TypeCode.Type_bool || t2 == TypeCode.Type_bool)
                throw new TypeException("EDiv error: exp is boolean");
            else
                return t1 ;
        }
        public TypeCode visit(CPP.Absyn.EPlus p , Env env)
        {
            //System.out.println ("EPlus");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EPlus error: exps not match");
            else if (t1 == TypeCode.Type_bool || t2 == TypeCode.Type_bool)
                throw new TypeException("EPlus error: exp is boolean");
            else
                return t1 ;       
        }
        public TypeCode visit(CPP.Absyn.EMinus p , Env env)
        {
            //System.out.println ("EMinus");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EMinus error: exps not match");
            else if (t1 == TypeCode.Type_bool || t2 == TypeCode.Type_bool)
                throw new TypeException("EMinus error: exp is boolean");
            else
                return t1 ;
        }
        public TypeCode visit(CPP.Absyn.ELt p , Env env)
        {
            //System.out.println ("ELt");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("ELt error: exps not match");

            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EGt p , Env env)
        {
            //System.out.println ("EGt");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EGt error: exps not match");

            return TypeCode.Type_bool ;

        }
        public TypeCode visit(CPP.Absyn.ELtEq p , Env env)
        {
            //System.out.println ("ELtEq");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("ELtEq error: exps not match");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EGtEq p , Env env)
        {
            //System.out.println ("EGtEq");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EGtEq error: exps not match");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EEq p , Env env)
        {
            //System.out.println ("EEq");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EEq error: exps not match");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.ENEq p , Env env)
        {
           // System.out.println ("ENEq");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("ENEq error: exps not match");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EAnd p , Env env)
        {
           // System.out.println ("EAnd");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != TypeCode.Type_bool || t2 != TypeCode.Type_bool )
                throw new TypeException("EAnd error: exps must be boolean type");
           
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EOr p , Env env)
        {
           // System.out.println ("EOr");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != TypeCode.Type_bool || t2 != TypeCode.Type_bool )
                throw new TypeException("EOr error: exps must be boolean type");
            return TypeCode.Type_bool ;
        }
        public TypeCode visit(CPP.Absyn.EAss p , Env env)
        {
           // System.out.println ("EAss");
            TypeCode t1 = checkExp(p.exp_1 , env);
            TypeCode t2 = checkExp(p.exp_2 , env);
            if(t1 != t2)
                throw new TypeException("EAss error: exps not match");
            return t1 ;
        }
    } 
    // function for TypeCode
    private TypeCode getTypeCode(Type t)
    {
        return t.accept(new TypeCoder() , null);
    }
    private class TypeCoder implements Type.Visitor<TypeCode , Object>
    {
        public TypeCode visit(Type_bool t, Object arg)
        {
            return TypeCode.Type_bool ; 
        }
        public TypeCode visit(Type_int t, Object arg)
        {
            return TypeCode.Type_int ;
        }
        public TypeCode visit(Type_double t, Object arg)
        {
            return TypeCode.Type_double ;
        }
        public TypeCode visit(Type_void t, Object arg)
        {
            return TypeCode.Type_void ;
        }

    }
}
