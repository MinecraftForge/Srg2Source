package net.minecraftforge.srg2source.util.io;

import java.io.PrintStream;

@SuppressWarnings("rawtypes")
public abstract class ConfLogger<T extends ConfLogger>
{
    protected PrintStream outLogger = System.out;
    protected PrintStream errorLogger = System.err;
    
    protected void log(String s)
    {
        outLogger.println(s);
    }
    
    public PrintStream getOutLogger()
    {
        return outLogger;
    }

    @SuppressWarnings("unchecked")
    public T setOutLogger(PrintStream outLogger)
    {
        this.outLogger = outLogger;
        return (T) this;
    }

    public PrintStream getErrorLogger()
    {
        return errorLogger;
    }

    @SuppressWarnings("unchecked")
    public T setErrorLogger(PrintStream errorLogger)
    {
        this.errorLogger = errorLogger;
        return (T) this;
    }
}
