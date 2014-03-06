package net.minecraftforge.srg2source.rangeapplier;

import java.io.Serializable;

import net.minecraftforge.srg2source.rangeapplier.LocalVarFile.LocalVar;
import net.minecraftforge.srg2source.util.ListFile;

public class LocalVarFile extends ListFile<LocalVar, LocalVarFile>
{
    @Override
    protected LocalVar parseLine(String line)
    {
        if (!line.startsWith("@"))
            return null;

        LocalVar var = new LocalVar(line);

        if (!var.kind.equals("localvar"))
            return null;

        return var;
    }

    public static final class LocalVar implements Cloneable, Serializable
    {
        private static final long serialVersionUID = 5728905738150467133L;
        public final int          startRange, endRange, variableIndex;
        public final String       absFileName, expectedOldText, kind, mcpClassName, mcpMethodName, mcpMethodSig, variableName;

        public LocalVar(String line)
        {
            String[] stuff = line.trim().split("\\|");
            // absFilename startRangeStr endRangeStr expectedOldText kind
            absFileName = stuff[1];
            startRange = Integer.parseInt(stuff[2]);
            endRange = Integer.parseInt(stuff[3]);
            expectedOldText = stuff[4];
            kind = stuff[5];
            // mcpClassName, mcpMethodName, mcpMethodSignature, variableName, variableIndex
            mcpClassName = stuff[6];
            mcpMethodName = stuff[7];
            mcpMethodSig = stuff[8];
            variableName = stuff[9];
            variableIndex = Integer.parseInt(stuff[10]);
        }

        public LocalVar(int startRange, int endRange, int variableIndex, String absFileName, String expectedOldText, String kind, String mcpClassName, String mcpMethodName, String mcpMethodSignature, String variableName)
        {
            super();
            this.startRange = startRange;
            this.endRange = endRange;
            this.variableIndex = variableIndex;
            this.absFileName = absFileName;
            this.expectedOldText = expectedOldText;
            this.kind = kind;
            this.mcpClassName = mcpClassName;
            this.mcpMethodName = mcpMethodName;
            this.mcpMethodSig = mcpMethodSignature;
            this.variableName = variableName;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((absFileName == null) ? 0 : absFileName.hashCode());
            result = prime * result + endRange;
            result = prime * result + ((expectedOldText == null) ? 0 : expectedOldText.hashCode());
            result = prime * result + ((kind == null) ? 0 : kind.hashCode());
            result = prime * result + ((mcpClassName == null) ? 0 : mcpClassName.hashCode());
            result = prime * result + ((mcpMethodName == null) ? 0 : mcpMethodName.hashCode());
            result = prime * result + ((mcpMethodSig == null) ? 0 : mcpMethodSig.hashCode());
            result = prime * result + startRange;
            result = prime * result + variableIndex;
            result = prime * result + ((variableName == null) ? 0 : variableName.hashCode());
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
            LocalVar other = (LocalVar) obj;
            if (absFileName == null)
            {
                if (other.absFileName != null)
                    return false;
            }
            else if (!absFileName.equals(other.absFileName))
                return false;
            if (endRange != other.endRange)
                return false;
            if (expectedOldText == null)
            {
                if (other.expectedOldText != null)
                    return false;
            }
            else if (!expectedOldText.equals(other.expectedOldText))
                return false;
            if (kind == null)
            {
                if (other.kind != null)
                    return false;
            }
            else if (!kind.equals(other.kind))
                return false;
            if (mcpClassName == null)
            {
                if (other.mcpClassName != null)
                    return false;
            }
            else if (!mcpClassName.equals(other.mcpClassName))
                return false;
            if (mcpMethodName == null)
            {
                if (other.mcpMethodName != null)
                    return false;
            }
            else if (!mcpMethodName.equals(other.mcpMethodName))
                return false;
            if (mcpMethodSig == null)
            {
                if (other.mcpMethodSig != null)
                    return false;
            }
            else if (!mcpMethodSig.equals(other.mcpMethodSig))
                return false;
            if (startRange != other.startRange)
                return false;
            if (variableIndex != other.variableIndex)
                return false;
            if (variableName == null)
            {
                if (other.variableName != null)
                    return false;
            }
            else if (!variableName.equals(other.variableName))
                return false;
            return true;
        }
    }
}
