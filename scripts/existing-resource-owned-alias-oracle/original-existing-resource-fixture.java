public class ExistingResourceFlow {
    static final class Resource implements AutoCloseable {
        final StringBuilder events; final boolean fail;
        Resource(StringBuilder events, boolean fail) { this.events=events;this.fail=fail; }
        public void close() { events.append("close;");if(fail)throw new IllegalStateException("close"); }
    }
    static String run(boolean bodyFail,boolean closeFail,boolean none) {
        StringBuilder events=new StringBuilder();final Resource scope=none?null:new Resource(events,closeFail);
        try { try(scope) { events.append("body;");if(bodyFail)throw new IllegalArgumentException("body"); } events.append("settled;"); }
        catch(RuntimeException failure) { events.append(failure.getClass().getSimpleName()+":"+failure.getMessage()+";");for(Throwable suppressed:failure.getSuppressed())events.append("suppressed="+suppressed.getMessage()+";"); }
        return events.toString();
    }
    public static void main(String[]args) {System.out.println(run(false,false,false));System.out.println(run(true,false,false));System.out.println(run(false,true,false));System.out.println(run(true,true,false));System.out.println(run(false,false,true));}
}
