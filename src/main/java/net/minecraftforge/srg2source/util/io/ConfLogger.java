package net.minecraftforge.srg2source.util.io;

import java.io.PrintStream;

@SuppressWarnings("rawtypes")
public abstract class ConfLogger<T extends ConfLogger>
{
    protected PrintStream logger = System.out;
    protected PrintStream errorLogger = System.err;

    protected void log(String s)
    {
        logger.println(s);
    }

    public PrintStream getLogger()
    {
        return logger;
    }

    @SuppressWarnings("unchecked")
    public T setLogger(PrintStream value)
    {
        this.logger = value;
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
