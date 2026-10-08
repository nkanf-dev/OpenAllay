package dev.openallay.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

public final class LoggerJava8Fixture {
    public static int run(){
        final List<LogRecord> records=new ArrayList<LogRecord>();String name="openallay.provider.fixture";
        Logger jul=Logger.getLogger(name);jul.setUseParentHandlers(false);jul.setLevel(Level.ALL);
        Handler handler=new Handler(){public void publish(LogRecord record){records.add(record);}public void flush(){}public void close(){}};
        handler.setLevel(Level.ALL);jul.addHandler(handler);
        OpenAllayLogger logger=new OpenAllayLogger(name);RuntimeException cause=new RuntimeException("fixture-cause");
        logger.info("zero");logger.warn("one {}",7);logger.error("two {} {}", "a", "b", cause);
        logger.info("extra {}","first","unused");logger.warn("missing {} {}","only");logger.error("cause-only",cause);
        int checks=0;
        if(records.size()!=6)throw new AssertionError("capture count");checks++;
        String[] wanted={"zero","one 7","two a b","extra first","missing only {}","cause-only"};
        Level[] levels={Level.INFO,Level.WARNING,Level.SEVERE,Level.INFO,Level.WARNING,Level.SEVERE};
        for(int i=0;i<6;i++){
            LogRecord r=records.get(i);if(!name.equals(r.getLoggerName())||!wanted[i].equals(r.getMessage())||r.getLevel().intValue()!=levels[i].intValue()||r.getThrown()!=(i==2||i==5?cause:null))throw new AssertionError("semantic logging record "+i);checks++;
            System.out.println("log="+r.getLoggerName()+":"+r.getLevel().intValue()+":"+r.getMessage()+":"+(r.getThrown()==null?"none":"fixture-cause"));
        }
        jul.setLevel(Level.OFF);final boolean[] formatted={false};Object lazy=new Object(){public String toString(){formatted[0]=true;throw new AssertionError("disabled formatting");}};
        logger.info("disabled {}",lazy);if(formatted[0]||records.size()!=6)throw new AssertionError("disabled format evaluated");checks++;
        jul.removeHandler(handler);return checks;
    }
}
