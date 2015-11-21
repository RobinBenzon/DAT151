package calc.Absyn; // Java Package generated by the BNF Converter.

public abstract class Exp implements java.io.Serializable {
  public abstract <R,A> R accept(Exp.Visitor<R,A> v, A arg);
  public interface Visitor <R,A> {
    public R visit(calc.Absyn.EAdd p, A arg);
    public R visit(calc.Absyn.ESub p, A arg);
    public R visit(calc.Absyn.EMul p, A arg);
    public R visit(calc.Absyn.EDiv p, A arg);
    public R visit(calc.Absyn.EInt p, A arg);

  }

}