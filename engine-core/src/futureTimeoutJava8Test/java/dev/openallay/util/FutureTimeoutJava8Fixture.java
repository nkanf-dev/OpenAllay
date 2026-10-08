package dev.openallay.util;
import java.util.concurrent.*;import java.lang.reflect.InvocationTargetException;
public final class FutureTimeoutJava8Fixture {
 public static void main(String[]args)throws Throwable{boolean modern=args.length==0;if(!modern&&!"1.8".equals(System.getProperty("java.specification.version")))throw new AssertionError("true8");
  CompletableFuture<String> value=CompletableFuture.completedFuture("value");check(deadline(value,0,TimeUnit.MILLISECONDS,modern)==value,"identity");check(value.join().equals("value"),"complete fastpath");
  CompletableFuture<String> before=new CompletableFuture<>();check(deadline(before,2,TimeUnit.SECONDS,modern)==before,"pendingidentity");before.complete("early");check(before.join().equals("early"),"earlycompletion");
  CompletableFuture<String> timeout=new CompletableFuture<>();deadline(timeout,10,TimeUnit.MILLISECONDS,modern);try{timeout.join();throw new AssertionError("timeoutmissing");}catch(CompletionException e){check(e.getCause()instanceof TimeoutException,"timeoutcause");check(e.getCause().getMessage()==null,"message");}check(!timeout.complete("late"),"latecannotreplace");
  CompletableFuture<String> zero=new CompletableFuture<>();deadline(zero,-1,TimeUnit.MILLISECONDS,modern);try{zero.join();throw new AssertionError("negtimeout");}catch(CompletionException e){check(e.getCause()instanceof TimeoutException,"negativecause");}
  CompletableFuture<String> canceled=new CompletableFuture<>();deadline(canceled,2,TimeUnit.SECONDS,modern);canceled.cancel(false);check(canceled.isCancelled(),"cancelpreserved");
  try{deadline(value,1,null,modern);throw new AssertionError("nullunit");}catch(NullPointerException expected){}
  RuntimeException exact=new RuntimeException("exact");CompletableFuture<String> failed=new CompletableFuture<>();failed.completeExceptionally(exact);deadline(failed,0,TimeUnit.MILLISECONDS,modern);try{failed.join();throw new AssertionError("failurelost");}catch(CompletionException e){check(e.getCause()==exact,"exactfailure");}
  System.out.println("identity/success/early/timeout-nullmsg/negative/cancel/nullunit/exactfailure");System.out.println("PASS standard future deadline originalmodern=canonicaltrue8");}
 @SuppressWarnings("unchecked") static<T>CompletableFuture<T>deadline(CompletableFuture<T>f,long time,TimeUnit unit,boolean modern)throws Throwable{if(!modern)return Java8Futures.orTimeout(f,time,unit);try{return(CompletableFuture<T>)CompletableFuture.class.getMethod("orTimeout",long.class,TimeUnit.class).invoke(f,time,unit);}catch(InvocationTargetException e){throw e.getCause();}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
