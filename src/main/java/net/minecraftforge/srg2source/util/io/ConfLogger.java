/*
 * Srg2Source
 * Copyright (c) 2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srg2source.util.io;

import java.io.PrintStream;

@SuppressWarnings("rawtypes")
public abstract class ConfLogger<T extends ConfLogger> {
    private PrintStream logger = System.out;
    private PrintStream errorLogger = System.err;

    protected void log(String s) {
        logger.println(s);
    }

    public void error(String s) {
        errorLogger.println(s);
    }

    public PrintStream getLogger() {
        return logger;
    }

    @SuppressWarnings("unchecked")
    public T setLogger(PrintStream value) {
        this.logger = value;
        return (T)this;
    }

    public PrintStream getErrorLogger() {
        return errorLogger;
    }

    @SuppressWarnings("unchecked")
    public T setErrorLogger(PrintStream errorLogger) {
        this.errorLogger = errorLogger;
        return (T)this;
    }
}
