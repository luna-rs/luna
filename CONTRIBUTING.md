# Contributing to Luna
Thanks for taking the time out to contribute to the Luna project. This guide explains how to:
 1. Maximize the chance of your changes being accepted
 2. Work on the Luna code base
 3. Get help if you encounter trouble
 
## Get in touch

We use GitHub issues to track bugs and enhancements. If you have a general usage question please ask on our [Discord server](https://discord.gg/udCqykV). The Luna team monitors the `#development` channel frequently.

Before starting to work on a feature or a fix, please open an issue to discuss the use case or bug with us. This can save both you and us a lot of time.
For any non-trivial change, we'll ask you to create a short design document explaining:

* Why is this change done? What's the use case?
* What test cases should it have? What could go wrong?
* How will it roughly be implemented? (We'll happily provide code pointers to save you time)

This can be done directly inside the GitHub issue.

If you are reporting a bug, please help to speed up problem diagnosis by providing as much information as possible.
Ideally, a detailed list of steps to reproduce the bug.

## Making changes
In order to make changes to Luna, you'll need:

* A text editor or IDE. We use and recommend [IntelliJ IDEA Community Edition](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://jdk.java.net/) (JDK) version 11.
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join).

Luna uses pull requests for contributions. Fork [luna-rs/luna](https://github.com/luna-rs/luna) and clone your fork. Configure your Git username and email with

    git config user.name 'First Last'
    git config user.email user@example.com

### IntelliJ

We suggest you use IntelliJ 2018.3.1 or newer.
- Open the `build.gradle.kts` file with IntelliJ and choose "Open as Project"
- Make sure "Create separate module per source set" is selected
- Make sure  "Use default gradle wrapper" is selected
- Select a Java 11 VM as "Gradle JVM"
- In the "File already exists" dialogue, choose "Yes" to overwrite
- In the "Open Project" dialogue, choose "Delete Existing Project and Import"
- Revert the Git changes to files in the `.idea` folder

### Eclipse

You can generate the Eclipse projects by running

    ./gradlew eclipse

Then you can import the generated projects into Eclipse

1. Install Eclipse 4.5 (Mars) at least
2. Install the Groovy Eclipse plugin from http://dist.springsource.org/snapshot/GRECLIPSE/e4.5/
3. Make sure you have a Java 8 compatible JDK configured in your workspace
4. In `Window->Preferences->Groovy->Compiler`, check `Enable Script folder support` and add `**/*.gradle`
5. Import all projects using the "Import Existing Projects into Workspace" wizard

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, please follow these guidelines when writing commit messages:

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`Issue: #123`) in the commit message on a separate line

### Submitting Your Change

After you submit your pull request, a Luna core developer will review it. It is normal that this takes several iterations, so don't get discouraged by change requests. They ensure the high quality code that we all enjoy.
If you do not get feedback after 3 days, feel free to ping `@maintainer` on our [Discord server](https://discord.gg/udCqykV).

When submitting pull requests, please
- Follow the current programming style present in the code (conventions, etc.)
- <b>Do not</b> submit white space changes as it makes it very hard to see the relevant changes you've made
- Make sure all new `.java` files have a simple Javadoc class comment, preferably with at least a paragraph describing what the class is for.
- A few unit tests would help a lot as well — someone has to do it.
- Look over the [Plugins conventions](https://github.com/lare96/luna/wiki/Writing-Plugins#conventions) section of the wiki if you are creating or maintaining a plugin.

## Getting Help

If you run into any trouble, please reach out to us on the issue you are working on. Alternatively, you ask for assistance on the `#help` channel on Discord.
