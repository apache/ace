# Apache Ace Release Guide
 
This document describes how to do a source release. It is based on the Apache
Development [Release FAQ] [1].

## Prerequisites

To create a release you must:

  * Have Subversion installed on your system;
  * Have gpg installed on your system;
  * Have a public key added to the [KEYS file] [2]; 
  * If you are using an http proxy, configure the following:

    export GRADLE_OPTS="-Dhttps.proxyHost=www.somehost.org -Dhttps.proxyPort=8080"

Before you can start staging a release candidate, you must:

  * Make sure there are no dependencies on snapshots/unreleased versions;
  * Increment the version parameter in build/build.gradle, if not already 
    done, and commit;
  * Under Bndtools, release (only) the bundles that needs to be released
    (using "Release workspace bundles" menu);
  * Create a tagged version of the sources in preparation of the release
    candidate.


## Release only the necessary bundles

Click on the Bndtools "Release workspace bundles", and release the bundles that
have been modified and need to be released.

Do *not* release bundles that are not modified or do not need a release (such 
as integration test bundles, i.e., all projects ending in `*.itest`).

Once done, under the shell prompt, go to the `cnf/releaserepo/` directory, run
`svn remove` to remove the previous (old) versions of the released bundles, and
run `svn add` to add the just released bundles.

Refresh the repositories using Bndtools, and commit all changes.  


## Create a tagged version

Creating a tagged version of the sources can be done directly through svn
(replace `r<n>` by the actual release number, like "r1"):

    v=<version> \
      svn copy https://svn.apache.org/repos/asf/ace/trunk \
      https://svn.apache.org/repos/asf/ace/releases/$v \
      -m "Release of Apache Ace $v"


## Staging a release candidate

Staging a release starts by checking out a tagged version of the sources 
(again, replace `<version>` by the actual release version, like "2.0.0"):

    v=<version> \
      svn co https://svn.apache.org/repos/asf/ace/releases/$v apache-ace-$v

The next step is to build/test the software and create the `release/staging/`
directory (where the source/jars will be packaged, again, replace `<version>`
by the actual release number, like "2.0.0"):

Use Java 8 as JDK

    $ cd apache-ace-<version>
    $ ./gradlew rat
    $ ./gradlew build runbundles export

Create the staging directory and artifacts (this will create `./build/staging/`
directory containing the signed release archives):

    $ ./gradlew makeStaging

You can upload the archives and the signatures to our development area, which 
we use to stage this release candidate. This development area can be found at 
`https://dist.apache.org/repos/dist/dev/ace` and adding files to it can be done
using "svnpubsub" which is taken care of by the following target:

    $ ./gradlew commitToStaging


## Voting on the release

Start a vote on the `dev@ace.apache.org` list, for example (be sure to replace 
`<version>` with the correct release number, like "1.0.0"):

    To: "Ace Developers List" <dev@ace.apache.org>
    Subject: [VOTE] Release of Apache Ace release r<n>
    
    Hi,
    
    We solved N issues in this release:
    http://issues.apache.org/jira/...
    
    There are still some outstanding issues:
    http://issues.apache.org/jira/...
    
    Staging repository:
    https://dist.apache.org/repos/dist/dev/ace/apache-ace-<version>/
    
    You can use this UNIX script to download the release and verify the signatures:
    http://svn.apache.org/repos/asf/ace/trunk/build/check_staged_ace_release.sh
    
    Usage:
    sh check_staged_ace_release.sh <version> /tmp/ace-staging
    
    This script, unlike the original check_staged_release.sh from Apache Felix,
    will download staging artifacts from https://dist.apache.org/repos/dist/dev/ace
    instead of http://repository.apache.org/content/repositories.
    
    Please vote to approve this release:
    
    [ ] +1 Approve the release
    [ ] -1 Veto the release (please provide specific comments)
    
    This vote will be open for 72 hours.


## Promoting the release:

Move the artifacts from the development area to the final release location at 
`https://dist.apache.org/repos/dist/release/ace` by invoking the following
target:

    $ ./gradlew promoteToRelease

Notify the `dev@ace.apache.org` list about the successful promotion and notify
the `users@ace.apache.org` list that a new release is available.

### Update the JIRA issues

Close all issues in JIRA that were fixed as part of the release and create a 
new version for the next release. This probably means that you need to rename
the existing `next` version to the released version of ACE and recreate a new
`next` version.


## Cancelling the release

To cancel the release for whatever reason, run:

    $ ./gradlew deleteFromStaging

And notify the `dev@ace.apache.org` list to make everybody aware of this. 

## References

[1]: http://www.apache.org/dev/release.html
[2]: http://www.apache.org/dist/ace/KEYS
