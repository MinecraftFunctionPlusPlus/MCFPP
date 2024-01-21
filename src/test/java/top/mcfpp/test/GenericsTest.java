package top.mcfpp.test;



public class GenericsTest {
    public static void main(String[] args) {
        Test<A> a = new Test<>();
        B b = new B();
        C c = new C();
        a.setVar(b);
        a.doSomething();
        a.setVar(c);
        a.doSomething();
    }
}

class Test<T extends A> {
    private T var;

    public T getVar() {
        return var;
    }

    public void setVar(T var) {
        this.var = var;
    }

    public void doSomething(){
        var.foo();
    }
}

class A{
    public void foo(){
        System.out.println("A.foo()");
    }
}

class B extends A {
    @Override
    public void foo() {
        System.out.println("B.foo()");
    }
}

class C extends A {
    @Override
    public void foo() {
        System.out.println("C.foo()");
    }
}