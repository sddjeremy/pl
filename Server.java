import java.io.*;
import java.lang.Thread.*;
import java.util.HashSet;
import java.util.concurrent.*;

class constants {
    public static final int A = 0;
    public static final int Z = 25;
    public static final int numLetters = 26;
}

class TransactionAbortException extends Exception {}
// this is intended to be caught
class TransactionUsageError extends Error {}
// this is intended to be fatal
class InvalidTransactionError extends Error {}
// bad input; will have to skip this transaction

// TO DO: you are not permitted to modify class Account
//
class Account {
    private int value = 0;
    private Thread writer = null;
    private HashSet<Thread> readers;

    public Account(int initialValue) {
        value = initialValue;
        readers = new HashSet<Thread>();
    }

    private void delay() {
        try {
            Thread.sleep(100);  // ms
        } catch(InterruptedException e) {}
            // Java requires you to catch that
    }

    public int peek() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer == self || readers.contains(self)) {
                // should do all peeks before opening account
                // (but *can* peek while another thread has open)
                throw new TransactionUsageError();
            }
            return value;
        }
    }

    // TO DO: the sequential version does not call this method,
    // but the parallel version will need to.
    //
    public void verify(int expectedValue)
        throws TransactionAbortException {
        delay();
        synchronized (this) {
            if (!readers.contains(Thread.currentThread())) {
                throw new TransactionUsageError();
            }
            if (value != expectedValue) {
                // somebody else modified value since we used it;
                // will have to retry
                throw new TransactionAbortException();
            }
        }
    }

    public void update(int newValue) {
        delay();
        synchronized (this) {
            if (writer != Thread.currentThread()) {
                throw new TransactionUsageError();
            }
            value = newValue;
        }
    }

    // TO DO: the sequential version does not open anything for reading
    // (verifying), but the parallel version will need to.
    //
    public void open(boolean forWriting)
        throws TransactionAbortException {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (forWriting) {
                if (writer == self) {
                    throw new TransactionUsageError();
                }
                int numReaders = readers.size();
                if (writer != null || numReaders > 1
                        || (numReaders == 1 && !readers.contains(self))) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                writer = self;
            } else {
                if (readers.contains(self) || (writer == self)) {
                    throw new TransactionUsageError();
                }
                if (writer != null) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                readers.add(Thread.currentThread());
            }
        }
    }

    public void close() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer != self && !readers.contains(self)) {
                throw new TransactionUsageError();
            }
            if (writer == self) writer = null;
            if (readers.contains(self)) readers.remove(self);
        }
    }

    // print value in wide output field
    public void print() {
        System.out.format("%11d", new Integer(value));
    }

    // print value % numLetters (indirection value) in 2 columns
    public void printMod() {
        int val = value % constants.numLetters;
        if (val < 10) System.out.print("0");
        System.out.print(val);
    }
}

// TO DO: Worker is currently an ordinary class.
// You will need to movify it to make it a task,
// so it can be given to an Executor thread pool.
//
class Worker implements Runnable {
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;

    private Account[] accounts;
    private Account[] cache;
    private String transaction;

    // TO DO: The sequential version of Worker peeks at accounts
    // whenever it needs to get a value, and opens, updates, and closes
    // an account whenever it needs to set a value.  This won't work in
    // the parallel version.  Instead, you'll need to cache values
    // you've read and written, and then, after figuring out everything
    // you want to do, (1) open all accounts you need, for reading,
    // writing, or both, (2) verify all previously peeked-at values,
    // (3) perform all updates, and (4) close all opened accounts.

    public Worker(Account[] allAccounts, String trans) {
        accounts = allAccounts;
        cache = new Account[accounts.length];
        transaction = trans;
    }

    // Accesses an account in the cache. If the account is not present, it is loaded from the master list.
    private Account getCached(int accountNum)
    {
        if(cache[accountNum] == null)
        {
            cache[accountNum] = new Account(accounts[accountNum].peek());
        }
        return cache[accountNum];
    }
    
    // Uses the account cache, not the master copy
    private Account parseAccount(String name) {
        int accountNum = (int) (name.charAt(0)) - (int) 'A';
        if (accountNum < A || accountNum > Z)
            throw new InvalidTransactionError();
        Account a = getCached(accountNum);
        for (int i = 1; i < name.length(); i++) {
            if (name.charAt(i) != '*')
                throw new InvalidTransactionError();
            accountNum = (getCached(accountNum).peek() % numLetters);
            a = getCached(accountNum);
        }
        return a;
    }

    private int parseAccountOrNum(String name) {
        int rtn;
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            rtn = new Integer(name).intValue();
        } else {
            rtn = parseAccount(name).peek();
        }
        return rtn;
    }

    public void run() {
        boolean failed = true;
        while(failed)
        {
            // tokenize transaction
            String[] commands = transaction.split(";");

            for (int i = 0; i < commands.length; i++) {
                String[] words = commands[i].trim().split("\\s");
                if (words.length < 3)
                    throw new InvalidTransactionError();
                Account lhs = parseAccount(words[0]);       // Our CACHED left hand side
                if (!words[1].equals("="))
                    throw new InvalidTransactionError();
                int rhs = parseAccountOrNum(words[2]);
                for (int j = 3; j < words.length; j+=2) {
                    if (words[j].equals("+"))
                        rhs += parseAccountOrNum(words[j+1]);
                    else if (words[j].equals("-"))
                        rhs -= parseAccountOrNum(words[j+1]);
                    else
                        throw new InvalidTransactionError();
                }
                // Open relevant accounts for verification
                // Read for != LHS, Write for LHS
                int lhsIndex = -1;
                for(int j = 0; j < cache.length; j++) {
                    if(cache[j] != null) {
                        // All for reading
                        while(true) {
                            try {
                                accounts[j].open(false);
                                //System.out.println(words[0] + " locked for reading " + (char)('A'+j));
                                break;
                            }
                            catch(TransactionAbortException e) {
                                // Keep on waiting because NO DEADLOCK :D
                            }
                        }
                        // Open the LHS for writing
                        if(lhs == cache[j])
                        {
                            lhsIndex = j;
                            while(true) {
                                try {
                                    accounts[j].open(true);
                                    //System.out.println(words[0] + " locked for writing " + (char)('A'+j));
                                    break;
                                }
                                catch(TransactionAbortException e) {
                                    // Keep on waiting because NO DEADLOCK :D
                                }
                            }
                        }
                    }
                }

                // Verify all opened accounts
                failed = false;
                for(int j = 0; j < cache.length && !failed; j++) {
                    if(cache[j] != null) {
                        try {
                            //System.out.println("Verifying " + (char)('A'+j) + " == " + cache[j].peek());
                            accounts[j].verify(cache[j].peek());
                        }
                        catch(TransactionAbortException e) {
                            failed = true;
                        }
                    }
                }

                // Commit!
                if(!failed)
                {
                    accounts[lhsIndex].update(rhs);
                    System.out.println("commit: " + transaction);
                }
                
                // Close all opened accounts
                for(int j = 0; j < cache.length; j++) {
                    if(cache[j] != null) {
                        accounts[j].close();
                        //System.out.println((char)('A' + lhsIndex) + " closed " + (char)('A'+j));
                    }
                }
            }
        }
    }
}

public class Server {
    private static final int A = constants.A;
    private static final int Z = constants.Z;
    private static final int numLetters = constants.numLetters;
    private static Account[] accounts;

    private static void dumpAccounts() {
        // output values:
        for (int i = A; i <= Z; i++) {
            System.out.print("    ");
            if (i < 10) System.out.print("0");
            System.out.print(i + " ");
            System.out.print(new Character((char) (i + 'A')) + ": ");
            accounts[i].print();
            System.out.print(" (");
            accounts[i].printMod();
            System.out.print(")\n");
        }
    }

    public static void main (String args[])
        throws IOException, InterruptedException {
        accounts = new Account[numLetters];
        for (int i = A; i <= Z; i++) {
            accounts[i] = new Account(Z-i);
        }

        // read transactions from input file
        String line;
        BufferedReader input =
            new BufferedReader(new FileReader(args[0]));

// TO DO: you will need to create an Executor and then modify the
// following loop to feed tasks to the executor instead of running them
// directly.  Don't modify the initialization of accounts above, or the
// output at the end.
        ExecutorService es = java.util.concurrent.Executors.newFixedThreadPool(26);

        while ((line = input.readLine()) != null) {
            Worker w = new Worker(accounts, line);
            es.execute(w);
        }

        es.shutdown();
        es.awaitTermination(60, TimeUnit.HOURS);
        es.shutdownNow();

        System.out.println("final values:");
        dumpAccounts();
    }
}
