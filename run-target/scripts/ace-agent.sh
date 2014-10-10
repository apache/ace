#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# -----------------------------------------------------------------------------
# This script was adapted from the Apache Tomcat Project.
# -----------------------------------------------------------------------------
#
# Control Script for the ACE Management Agent
#
# Environment Variable Prerequisites
#
#   ACE_HOME       May point at your ACE Management Agent "build" directory.
#
#   ACE_BASE       (Optional) Base directory for resolving dynamic portions
#                   of an ACE Management Agent installation.  If not present, resolves to
#                   the same directory that ACE_HOME points to.
#
#   ACE_OUT        (Optional) Full path to a file where stdout and stderr
#                   will be redirected.
#                   Default is $ACE_BASE/logs/ace.out
#
#   ACE_OPTS       (Optional) Java runtime options used when the "start",
#                   "run" or "debug" command is executed.
#                   Include here and not in JAVA_OPTS all options, that should
#                   only be used by the ACE Management Agent itself, not by the stop process,
#                   the version command etc.
#                   Examples are heap size, GC logging, JMX ports etc.
#
#   ACE_TMPDIR     (Optional) Directory path location of temporary directory
#                   the JVM should use (java.io.tmpdir).  Defaults to
#                   $ACE_BASE/temp.
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#                   Required to run the with the "debug" argument.
#
#   JRE_HOME        Must point at your Java Runtime installation.
#                   Defaults to JAVA_HOME if empty. If JRE_HOME and JAVA_HOME
#                   are both set, JRE_HOME is used.
#
#   JAVA_OPTS       (Optional) Java runtime options used when any command
#                   is executed.
#                   Include here and not in ACE_OPTS all options, that
#                   should be used by the ACE Management Agent and also by the stop process,
#                   the version command etc.
#                   Most options should go into ACE_OPTS.
#
#   JAVA_ENDORSED_DIRS (Optional) Lists of of colon separated directories
#                   containing some jars in order to allow replacement of APIs
#                   created outside of the JCP (i.e. DOM and SAX from W3C).
#                   It can also be used to update the XML parser implementation.
#                   Defaults to $ACE_HOME/endorsed.
#
#   JPDA_TRANSPORT  (Optional) JPDA transport used when the "jpda start"
#                   command is executed. The default is "dt_socket".
#
#   JPDA_ADDRESS    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. The default is localhost:8000.
#
#   JPDA_SUSPEND    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. Specifies whether JVM should suspend
#                   execution immediately after startup. Default is "n".
#
#   JPDA_OPTS       (Optional) Java runtime options used when the "jpda start"
#                   command is executed. If used, JPDA_TRANSPORT, JPDA_ADDRESS,
#                   and JPDA_SUSPEND are ignored. Thus, all required jpda
#                   options MUST be specified. The default is:
#
#                   -agentlib:jdwp=transport=$JPDA_TRANSPORT,
#                       address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND
#
#   ACE_PID         Path of the file which should contains the pid
#                   of the ACE Management Agent startup java process, when start
#                   (fork) is used
# -----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
esac

# Make sure prerequisite environment variables are set
if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
  if $darwin; then
    # Bugzilla 54390
    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`
    # Bugzilla 37284 (reviewed).
    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`which java 2>/dev/null`
    if [ "x$JAVA_PATH" != "x" ]; then
      JAVA_PATH=`dirname $JAVA_PATH 2>/dev/null`
      JRE_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
    if [ "x$JRE_HOME" = "x" ]; then
      # XXX: Should we try other locations?
      if [ -x /usr/bin/java ]; then
        JRE_HOME=/usr
      fi
    fi
  fi
  if [ -z "$JAVA_HOME" -a -z "$JRE_HOME" ]; then
    echo "Neither the JAVA_HOME nor the JRE_HOME environment variable is defined"
    echo "At least one of these environment variable is needed to run this program"
    exit 1
  fi
fi
if [ -z "$JAVA_HOME" -a "$1" = "debug" ]; then
  echo "JAVA_HOME should point to a JDK in order to run in debug mode."
  exit 1
fi
if [ -z "$JRE_HOME" ]; then
  JRE_HOME="$JAVA_HOME"
fi

# If we're running under jdb, we need a full jdk.
if [ "$1" = "debug" ] ; then
  if [ ! -x "$JAVA_HOME"/bin/java -o ! -x "$JAVA_HOME"/bin/jdb -o ! -x "$JAVA_HOME"/bin/javac ]; then
    echo "The JAVA_HOME environment variable is not defined correctly"
    echo "This environment variable is needed to run this program"
    echo "NB: JAVA_HOME should point to a JDK not a JRE"
    exit 1
  fi
fi

# Set standard commands for invoking Java, if not already set.
if [ -z "$_RUNJAVA" ]; then
  _RUNJAVA="$JRE_HOME"/bin/java
fi
if [ "$os400" != "true" ]; then
  if [ -z "$_RUNJDB" ]; then
    _RUNJDB="$JAVA_HOME"/bin/jdb
  fi
fi

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set ACE_HOME if not already set
[ -z "$ACE_HOME" ] && ACE_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

# Copy ACE_BASE from ACE_HOME if not already set
[ -z "$ACE_BASE" ] && ACE_BASE="$ACE_HOME"

# Ensure that neither ACE_HOME nor ACE_BASE contains a colon
# as this is used as the separator in the classpath and Java provides no
# mechanism for escaping if the same character appears in the path.
case $ACE_HOME in
  *:*) echo "Using ACE_HOME:   $ACE_HOME";
       echo "Unable to start as ACE_HOME contains a colon (:) character";
       exit 1;
esac
case $ACE_BASE in
  *:*) echo "Using ACE_BASE:   $ACE_BASE";
       echo "Unable to start as ACE_BASE contains a colon (:) character";
       exit 1;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$ACE_HOME" ] && ACE_HOME=`cygpath --unix "$ACE_HOME"`
  [ -n "$ACE_BASE" ] && ACE_BASE=`cygpath --unix "$ACE_BASE"`
fi

if [ -z "$ACE_OUT" ] ; then
  ACE_OUT="$ACE_BASE"/logs/ace.out
fi
mkdir -p `dirname "$ACE_OUT"` 2>/dev/null

if [ -z "$ACE_TMPDIR" ] ; then
  # Define the java.io.tmpdir to use for ACE Management Agent
  ACE_TMPDIR="$ACE_BASE"/temp
fi
mkdir -p "$ACE_TMPDIR"

# Bugzilla 37848: When no TTY is available, don't output to console
have_tty=0
if [ "`tty`" != "not a tty" ]; then
    have_tty=1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  JRE_HOME=`cygpath --absolute --windows "$JRE_HOME"`
  ACE_HOME=`cygpath --absolute --windows "$ACE_HOME"`
  ACE_BASE=`cygpath --absolute --windows "$ACE_BASE"`
  ACE_TMPDIR=`cygpath --absolute --windows "$ACE_TMPDIR"`
  JAVA_ENDORSED_DIRS=`cygpath --path --windows "$JAVA_ENDORSED_DIRS"`
fi

# Uncomment the following line to make the umask available when using the
# org.apache.ace.security.SecurityListener (which we don't have at this point)
#JAVA_OPTS="$JAVA_OPTS -Dorg.apache.ace.security.SecurityListener.UMASK=`umask`"

# Need a PID directory
if [ -z "$ACE_PID" ]; then
  ACE_PID="$ACE_BASE/run/ace.pid"
  mkdir -p "$ACE_BASE/run" 2>/dev/null
fi

# ----- Execute The Requested Command -----------------------------------------

# Bugzilla 37848: only output this if we have a TTY
if [ $have_tty -eq 1 ]; then
  echo "Using ACE_BASE:   $ACE_BASE"
  echo "Using ACE_HOME:   $ACE_HOME"
  echo "Using ACE_TMPDIR: $ACE_TMPDIR"
  if [ "$1" = "debug" ] ; then
    echo "Using JAVA_HOME:  $JAVA_HOME"
  else
    echo "Using JRE_HOME:   $JRE_HOME"
  fi
  echo "Using ACE_PID:    $ACE_PID"
fi

if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="localhost:8000"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="n"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND"
  fi
  ACE_OPTS="$ACE_OPTS $JPDA_OPTS"
  shift
fi

if [ "$1" = "debug" ] ; then
  shift
  if [ "$1" = "-security" ] ; then
    if [ $have_tty -eq 1 ]; then
      echo "Using Security Manager"
    fi
    shift
    exec "$_RUNJDB" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" \
      -sourcepath "$ACE_HOME"/../../java \
      -Djava.security.manager \
      -Djava.security.policy=="$ACE_BASE"/ace.policy \
      -Dace.agent.base="$ACE_BASE" \
      -Dace.agent.home="$ACE_HOME" \
      -Djava.io.tmpdir="$ACE_TMPDIR" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start
  else
      exec "$_RUNJDB" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" \
      -sourcepath "$ACE_HOME"/../../java \
      -Dace.agent.base="$ACE_BASE" \
      -Dace.agent.home="$ACE_HOME" \
      -Djava.io.tmpdir="$ACE_TMPDIR" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start
  fi
elif [ "$1" = "run" ]; then

  shift
  if [ "$1" = "-security" ] ; then
    if [ $have_tty -eq 1 ]; then
      echo "Using Security Manager"
    fi
    shift
    eval exec "\"$_RUNJAVA\"" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="\"$JAVA_ENDORSED_DIRS\"" \
      -Djava.security.manager \
      -Djava.security.policy=="\"$ACE_BASE/ace.policy\"" \
      -Dace.agent.base="\"$ACE_BASE\"" \
      -Dace.agent.home="\"$ACE_HOME\"" \
      -Djava.io.tmpdir="\"$ACE_TMPDIR\"" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start
  else
    eval exec "\"$_RUNJAVA\"" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="\"$JAVA_ENDORSED_DIRS\"" \
      -Dace.agent.base="\"$ACE_BASE\"" \
      -Dace.agent.home="\"$ACE_HOME\"" \
      -Djava.io.tmpdir="\"$ACE_TMPDIR\"" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start
  fi

elif [ "$1" = "start" ] ; then

  if [ ! -z "$ACE_PID" ]; then
    if [ -f "$ACE_PID" ]; then
      if [ -s "$ACE_PID" ]; then
        echo "Existing PID file found during start."
        if [ -r "$ACE_PID" ]; then
          PID=`cat "$ACE_PID"`
          ps -p $PID >/dev/null 2>&1
          if [ $? -eq 0 ] ; then
            echo "The ACE Management Agent appears to still be running with PID $PID. Start aborted."
            exit 1
          else
            echo "Removing/clearing stale PID file."
            rm -f "$ACE_PID" >/dev/null 2>&1
            if [ $? != 0 ]; then
              if [ -w "$ACE_PID" ]; then
                cat /dev/null > "$ACE_PID"
              else
                echo "Unable to remove or clear stale PID file. Start aborted."
                exit 1
              fi
            fi
          fi
        else
          echo "Unable to read PID file. Start aborted."
          exit 1
        fi
      else
        rm -f "$ACE_PID" >/dev/null 2>&1
        if [ $? != 0 ]; then
          if [ ! -w "$ACE_PID" ]; then
            echo "Unable to remove or write to empty PID file. Start aborted."
            exit 1
          fi
        fi
      fi
    fi
  fi

  shift
  touch "$ACE_OUT"
  if [ "$1" = "-security" ] ; then
    if [ $have_tty -eq 1 ]; then
      echo "Using Security Manager"
    fi
    shift
    eval "\"$_RUNJAVA\"" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="\"$JAVA_ENDORSED_DIRS\"" \
      -Djava.security.manager \
      -Djava.security.policy=="\"$ACE_BASE/ace.policy\"" \
      -Dace.agent.base="\"$ACE_BASE\"" \
      -Dace.agent.home="\"$ACE_HOME\"" \
      -Djava.io.tmpdir="\"$ACE_TMPDIR\"" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start \
      >> "$ACE_OUT" 2>&1 "&"

  else
    eval "\"$_RUNJAVA\"" $JAVA_OPTS $ACE_OPTS \
      -Djava.endorsed.dirs="\"$JAVA_ENDORSED_DIRS\"" \
      -Dace.agent.base="\"$ACE_BASE\"" \
      -Dace.agent.home="\"$ACE_HOME\"" \
      -Djava.io.tmpdir="\"$ACE_TMPDIR\"" \
      -jar "$ACE_HOME"/bin/ace-launcher.jar "$@" start \
      >> "$ACE_OUT" 2>&1 "&"

  fi

  if [ ! -z "$ACE_PID" ]; then
    echo $! > "$ACE_PID"
  fi

  echo "ACE Management Agent started."

elif [ "$1" = "stop" ] ; then

  shift
  SLEEP=5
  if [ ! -z "$1" ]; then
    echo $1 | grep "[^0-9]" >/dev/null 2>&1
    if [ $? -gt 0 ]; then
      SLEEP=$1
      shift
    fi
  fi

  FORCE=0
  if [ "$1" = "-force" ]; then
    shift
    FORCE=1
  fi

  if [ ! -z "$ACE_PID" ]; then
    if [ -f "$ACE_PID" ]; then
      if [ -s "$ACE_PID" ]; then
        kill -0 `cat "$ACE_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          echo "PID file found but no matching process was found. Stop aborted."
          exit 1
        fi
      else
        echo "PID file is empty and has been ignored."
      fi
    else
      echo "\$ACE_PID was set but the specified file does not exist. Is ACE Management Agent running? Stop aborted."
      exit 1
    fi
  fi

  # Try a normal kill.
  if [ ! -z "$ACE_PID" ]; then
    echo "Attempting to signal the process to stop through OS signal."
    kill -15 `cat "$ACE_PID"` >/dev/null 2>&1
  fi

  if [ ! -z "$ACE_PID" ]; then
    if [ -f "$ACE_PID" ]; then
      while [ $SLEEP -ge 0 ]; do
        kill -0 `cat "$ACE_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          rm -f "$ACE_PID" >/dev/null 2>&1
          if [ $? != 0 ]; then
            if [ -w "$ACE_PID" ]; then
              cat /dev/null > "$ACE_PID"
              # If the ACE Management Agent has stopped don't try and force a stop with an empty PID file
              FORCE=0
            else
              echo "The PID file could not be removed or cleared."
            fi
          fi
          echo "ACE Management Agent stopped."
          break
        fi
        if [ $SLEEP -gt 0 ]; then
          sleep 1
        fi
        if [ $SLEEP -eq 0 ]; then
          if [ $FORCE -eq 0 ]; then
            echo "ACE Management Agent did not stop in time. PID file was not removed. To aid diagnostics a thread dump has been written to standard out."
            kill -3 `cat "$ACE_PID"`
          fi
        fi
        SLEEP=`expr $SLEEP - 1 `
      done
    fi
  fi

  KILL_SLEEP_INTERVAL=5
  if [ $FORCE -eq 1 ]; then
    if [ -z "$ACE_PID" ]; then
      echo "Kill failed: \$ACE_PID not set"
    else
      if [ -f "$ACE_PID" ]; then
        PID=`cat "$ACE_PID"`
        echo "Killing the ACE Management Agent with the PID: $PID"
        kill -9 $PID
        while [ $KILL_SLEEP_INTERVAL -ge 0 ]; do
            kill -0 `cat "$ACE_PID"` >/dev/null 2>&1
            if [ $? -gt 0 ]; then
                if [ $? != 0 ]; then
                    if [ -w "$ACE_PID" ]; then
                        cat /dev/null > "$ACE_PID"
                    else
                        echo "The PID file could not be removed."
                    fi
                fi
                # Set this to zero else a warning will be issued about the process still running
                KILL_SLEEP_INTERVAL=0
                echo "The ACE Management Agent process has been killed."
                break
            fi
            if [ $KILL_SLEEP_INTERVAL -gt 0 ]; then
                sleep 1
            fi
            KILL_SLEEP_INTERVAL=`expr $KILL_SLEEP_INTERVAL - 1 `
        done
        if [ $KILL_SLEEP_INTERVAL -gt 0 ]; then
            echo "The ACE Management Agent has not been killed completely yet. The process might be waiting on some system call or might be UNINTERRUPTIBLE."
        fi
      fi
    fi
  fi

elif [ "$1" = "version" ] ; then
                rm -f "$ACE_PID" >/dev/null 2>&1

    "$_RUNJAVA"   \
      -jar "$ACE_HOME/bin/ace-launcher.jar" version
else

  echo "Usage: ace-agent.sh ( commands ... )"
  echo "commands:"
  echo "  debug             Start the ACE Management Agent in a debugger"
  echo "  debug -security   Debug the ACE Management Agent with a security manager"
  echo "  jpda start        Start the ACE Management Agent under JPDA debugger"
  echo "  run               Start the ACE Management Agent in the current window"
  echo "  run -security     Start in the current window with security manager"
  echo "  start             Start the ACE Management Agent in a separate window"
  echo "  start -security   Start in a separate window with security manager"
  echo "  stop              Stop the ACE Management Agent, waiting up to 5 seconds for the process to end"
  echo "  stop n            Stop the ACE Management Agent, waiting up to n seconds for the process to end"
  echo "  stop -force       Stop the ACE Management Agent, wait up to 5 seconds and then use kill -KILL if still running"
  echo "  stop n -force     Stop the ACE Management Agent, wait up to n seconds and then use kill -KILL if still running"
#  echo "  version           What version of the ACE Management Agent are you running?"
  echo "Note: Waiting for the process to end and use of the -force option require that \$ACE_PID is defined"
  exit 1
fi