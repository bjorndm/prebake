#!/usr/bin/perl

use strict;

if ($#ARGV < 0) {
  print "Usage: $0 <output-dir> <source-file>...

Collects tasks from source files and generates an HTML report under <output-dir>
by grepping through <source-file>s for comments containing the word \"TODO\".

Can be invoked like
  mkdir report-dir && find src -name \*.java | xargs $0 report-dir
";
  exit(0);
}

my $outDir = shift(@ARGV);
my @files = @ARGV;

die "Bad output directory $outDir" unless -d $outDir;
foreach my $sourceFile (sort(@ARGV)) {
  die "Bad source file $sourceFile" unless -f $sourceFile;
}

sub html($) {
  my $s = $_[0];
  $s =~ s/&/&amp;/g;
  $s =~ s/"/&#34;/g;
  $s =~ s/'/&#39;/g;
  $s =~ s/</&lt;/g;
  $s =~ s/>/&gt;/g;
  return $s;
}

sub uri($) {
  my $s = $_[0];
  $s =~ s/%/%25/g;
  $s =~ s/\?/%3f/g;
  $s =~ s/#/%23/g;
  $s =~ s/&/%26/g;
  return $s;
}

sub basename($) {
  my $path = $_[0];
  $path =~ s/\/[^\/]*$//;
  return $path;
}

sub mkdirs($) {
  my $dir = $_[0];
  if ($dir) {
    if (! -e $dir) {
      mkdirs(basename($dir));
      mkdir($dir);
    }
  }
}

my $summaryBody = '';

my @ringBuffer = ('', '', '', '', '');
my $ringBufferIdx = 0;
my $ringBufferLen = scalar(@ringBuffer);
my $ringBufferHalf = int($ringBufferLen / 2);
my @tasks;
my $nTasks = 0;

sub line($) {
  return $ringBuffer[($ringBufferIdx + $_[0]) % $ringBufferLen];
}

sub checkForTaskAndIncrement($) {
  my $sourceFile = $_[0];
  # If there is a task in the middle of the ring buffer, gather all the lines
  # into a snippet and export it.
  my $line = line($ringBufferHalf);
  if ($line =~ /\bTODO\b/) {
    my $tail = $line;
    $tail =~ s/^.*?\bTODO\b//;
    $tail =~ s/^(.{60}).{3}/$1.../;
    my $snippet = '';
    for (my $i = 0; $i < $ringBufferLen; $i++) {
      if ($i == $ringBufferHalf) { $snippet .= '<b>'; }
      $snippet .= html(line($i));
      if ($i == $ringBufferHalf) { $snippet .= '</b>'; }
    }
    my $detailsUrl = html(uri($sourceFile) . "-tasks.html");
    my $taskIdx = scalar(@tasks);
    my $lineNum = ($. - $ringBufferHalf);
    if ($#tasks < 0) {
      $summaryBody .= "<li><h2><a href=\"$detailsUrl\">"
          . html($sourceFile) . "</a></h2><ol>\n";
    }
    push(@tasks,
         "Line $lineNum\n"
         . "<pre class=\"prettyprint\" id=\"task-$taskIdx\">" . $snippet
         . "</pre>");
    $summaryBody .= "<li><a href=\"$detailsUrl#task-$taskIdx"
        . "\">$lineNum : <nobr>" . html($tail) . "</nobr></a></li>";
    ++$nTasks;
  }
  $ringBufferIdx = ($ringBufferIdx + 1) % $ringBufferLen;
}

foreach my $sourceFile (sort(@ARGV)) {
  for (my $i = $#ringBuffer; $i >= 0; --$i) { $ringBuffer[$i] = ''; }
  @tasks = ();

  open(IN, "<$sourceFile") or die "$!: $sourceFile";
  while (<IN>) {
    $ringBuffer[$ringBufferIdx] = $_;
    checkForTaskAndIncrement($sourceFile);
  }
  close(IN);
  for (my $i = $ringBufferLen - $ringBufferHalf; --$i >= 0;) {
    $ringBuffer[$ringBufferIdx] = '';
    checkForTaskAndIncrement($sourceFile);
  }

  if (@tasks) {
    my $outFile = "$outDir/$sourceFile-tasks.html";
    mkdirs(basename($outFile));
    open(OUT, ">$outFile") or die "$!: $outFile";
    print OUT qq'<html>
<head>
  <title>' . html($sourceFile) . qq'</title>
</head>
<body><ol>\n';
    foreach my $task (@tasks) {
      print OUT "<li>$task</li>\n";
    }
    print OUT qq'
  <link rel=stylesheet
   href="http://google-code-prettify.googlecode.com/svn/trunk/src/prettify.css" />
  <script
   src="http://google-code-prettify.googlecode.com/svn/trunk/src/lang-css.js">
  </script>
  <script
   src="http://google-code-prettify.googlecode.com/svn/trunk/src/prettify.js">
  </script>
  <script>prettyPrint()</script>
</ol></body></html>';
    close(OUT);
    $summaryBody .= "</ol>\n";
  }
}

my $reportFile = "$outDir/index.html";
open(OUT, ">$reportFile") or die "$!: $reportFile";
print OUT qq'<html>
<head>
  <title>Tasks</title>
</head>
<body><h1>$nTasks task' . ($nTasks == 1 ? '' : 's') . qq'</h1>
<ul>
$summaryBody
</ul></body></html>';
close(OUT);
