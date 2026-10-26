package dev.betterendfield.android;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

final class BemHotSwitchUpdateTest {
    private static int checks;
    private static void check(boolean result,String message) {
        checks++;if(!result) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        BemHotSwitchUpdate transaction=new BemHotSwitchUpdate("old");
        AtomicInteger preparations=new AtomicInteger(),publications=new AtomicInteger();
        check(!transaction.update(()->"old",index->{preparations.incrementAndGet();return index;},
            configuration->{publications.incrementAndGet();return true;}),"unchanged index published");
        check(preparations.get()==0&&publications.get()==0,"unchanged index did work");

        AtomicInteger reads=new AtomicInteger();
        check(!transaction.update(()->reads.incrementAndGet()==1?"first":"second",
            index->{preparations.incrementAndGet();return "config:"+index;},
            configuration->{publications.incrementAndGet();return true;}),"superseded index published");
        check(publications.get()==0,"superseded candidate reached publisher");

        check(!transaction.update(()->"second",index->{preparations.incrementAndGet();return "config:"+index;},
            configuration->{publications.incrementAndGet();return false;}),"rejected queue succeeded");
        check(preparations.get()==2&&publications.get()==1,"queue rejection lost prepared candidate");
        check(transaction.update(()->"second",index->{preparations.incrementAndGet();return "config:"+index;},
            configuration->{publications.incrementAndGet();check(configuration.equals("config:second"),"wrong candidate");return true;}),
            "retry did not publish");
        check(preparations.get()==2&&publications.get()==2,"queue retry re-prepared candidate");
        check(!transaction.update(()->"second",index->{throw new AssertionError("unexpected preparation");},
            configuration->{throw new AssertionError("unexpected publication");}),"duplicate publication");

        try {new BemHotSwitchUpdate(null);throw new AssertionError("null accepted");}
        catch(IOException expected){checks++;}
        try {transaction.update(()->"x".repeat(1024*1024+1),index->index,configuration->true);
            throw new AssertionError("oversized index accepted");}
        catch(IOException expected){checks++;}
        try {transaction.update(()->"界".repeat(400000),index->index,configuration->true);
            throw new AssertionError("UTF-8 oversized index accepted");}
        catch(IOException expected){checks++;}
        try {transaction.update(()->"third",index->null,configuration->true);
            throw new AssertionError("null configuration accepted");}
        catch(IOException expected){checks++;}
        try {transaction.update(()->"third",index->"界".repeat(400000),configuration->true);
            throw new AssertionError("UTF-8 oversized configuration accepted");}
        catch(IOException expected){checks++;}
        System.out.println("BemHotSwitchUpdateTest: "+checks+" checks passed");
    }
}
