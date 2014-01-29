package net.minecraftforge.srg2source.rangeapplier;

public class MethodData
{
    public final String name, sig;

    public MethodData(String name, String sig)
    {
        this.name = name;
        this.sig = sig;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((sig == null) ? 0 : sig.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MethodData other = (MethodData) obj;
        if (name == null)
        {
            if (other.name != null)
                return false;
        }
        else if (!name.equals(other.name))
            return false;
        if (sig == null)
        {
            if (other.sig != null)
                return false;
        }
        else if (!sig.equals(other.sig))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return name + " " + sig;
    }
}
