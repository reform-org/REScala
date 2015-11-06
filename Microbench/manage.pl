#!/usr/bin/perl
use 5.010;

use strict;
use warnings;
use utf8;
use English;
no if $] >= 5.018, warnings => "experimental::smartmatch";

use Cwd 'abs_path';
use File::Basename;
use Data::Dumper;

my $MAINDIR = dirname(__FILE__);
chdir $MAINDIR;

my $EXECUTABLE = './target/start';
if ($OSNAME eq "MSWin32") {
  $EXECUTABLE =~ s#/#\\#g;
}
my $OUTDIR = 'out';
my $RESULTDIR = 'results';
my $BSUB_TIME = "23:30";
my $BSUB_QUEUE = "deflt_auto";
my $BSUB_REQUIRE = "select[ mpi && avx ]";
my $BSUB_CORES = "16";

my @ENGINES = qw<parrp stm synchron>;
my @THREADS = (1..16);
my @STEPS = (1..16,24,32,64);
my @SIZES = (1,10,25,100,250,1000);
my @PHILOSOPHERS = (16, 32, 48, 64, 96, 128);
my @LAYOUTS = qw<alternating random block third>;
my %BASECONFIG = (
  si => "false", # synchronize iterations
  wi => 10, # warmup iterations
  w => "1000ms", # warmup time
  f => 3, # forks
  i => 5, # iterations
  r => "1000ms", # time per iteration
  to => "10s", #timeout
);

# stop java from formating numbers with `,` instead of `.`
$ENV{'LANG'} = 'en_US.UTF-8';
# my $OWNPATH = abs_path($0);
# $OWNPATH =~ s#/[^/]*$##;
# my $JMH_CLASSPATH = qq[-cp "$OWNPATH/Benchmarks/target/scala-2.11/jmh-classes"];
# say $JMH_CLASSPATH;
# $ENV{'JAVA_OPTS'} = $JMH_CLASSPATH;

my $command = shift @ARGV;
my @RUN = @ARGV ? @ARGV : qw< dynamicPhilosophers philosophers simpleChain simpleFan singleDynamic singleVar turnCreation >;

say "selected: " . (join " ", sort @RUN);
say "available: " . (join " ", sort keys %{&selection()});

given($command) {
  when ("show") { say Dumper([ makeRuns() ]) }
  when ("init") { init() }
  when ("run") { run() }
  when ("submit") { submitAll() }
  when ("submitsingle") { submitAllAsOne() }
};

sub init {
  mkdir $RESULTDIR;
  mkdir $OUTDIR;
  mkdir "$RESULTDIR/$_" for @RUN;
  chdir "..";
  system('sbt','project microbench', 'clean', 'stage', 'compileJmh');
  #system('sbt','clean', 'jmh:compile', 'jmh:stage');
  chdir $MAINDIR;
}

sub run {
  my @runs = makeRuns();
  for my $run (@runs) {
    my $prog = $run->{program};
    say "executing $prog";
    system($prog);
  }
}

sub submitAll {
  for my $run (makeRuns()) {
    say "submitting ", $run->{name};
    submit(hhlrjob($run->{name}, $run->{program}));
  }
}

sub submitAllAsOne {
  my $allRuns = join "\n", (map { $_->{program} } makeRuns());
  submit(hhlrjob("combined run", $allRuns));
}

sub submit {
  my ($job) = @_;
  open (my $BSUB, "|-", qq[bsub -q $BSUB_QUEUE -x -n $BSUB_CORES -W $BSUB_TIME -R "$BSUB_REQUIRE" ]);
  print $BSUB $job;
  close $BSUB;
}

sub makeRunString {
  my ($prefix, $name, $args, @benchmarks) = @_;
  my %arguments = %$args;
  my %params = %{delete $arguments{p}};
  my $paramstring =
    (join ' ',
      (map {"-$_ " . $arguments{$_}} keys %arguments),
      (map {"-p $_=" . $params{$_}} keys %params),
      (join " ", @benchmarks)
    );
  "$EXECUTABLE -rf csv -rff \"results/$prefix/$name.csv\" $paramstring"
}

sub makeRuns {
  my @runs;
  for my $run (@RUN) {
    push @runs, selectRun($run);
  }
  @runs;
}

sub selectRun {
  my ($run) = @_;

  my %selection = %{selection()};
  if (defined $selection{$run}) {
    return $selection{$run}->();
  }
  else {
    say "unknown: $run";
    return ();
  }

}

sub fromBaseConfig {
  my %extend = (%BASECONFIG, @_);
  return \%extend;
}

sub selection {
  return {
    # simple => sub {
    #   my @runs;

    #   for my $size (@THREADS) {
    #     my $name = "threads-$size";
    #     my $program = makeRunString("simple", $name,
    #       fromBaseConfig(
    #         p => { # parameters
    #           riname => (join ',', @FRAMEWORKS),
    #         },
    #         t => $size, #threads
    #       ),
    #       "simple.Mapping"
    #     );
    #     push @runs, {name => $name, program => $program};
    #   }

    #   @runs;
    # },

    # prim => sub {
    #   my @runs;

    #   for my $size (@THREADS) {
    #     my $name = "size-$size";
    #     my $program = makeRunString("prim", $name,
    #       fromBaseConfig(
    #         p => { # parameters
    #           depth => 64,
    #           sources => 64,
    #           riname => (join ',', @FRAMEWORKS),
    #         },
    #         t => $size, #threads
    #       ),
    #       ".*prim"
    #     );
    #     push @runs, {name => $name, program => $program};
    #   }

    #   @runs;
    # },

    philosophers => sub {
      my @runs;

      for my $threads (@THREADS) {
        for my $layout (@LAYOUTS) {
          my $name = "threads-$threads-layout-$layout";
          my $program = makeRunString("philosophers", $name,
            fromBaseConfig(
              p => { # parameters
                tableType => 'static',
                engineName => (join ',', @ENGINES),
                philosophers => (join ',', grep {$_ >= ($threads * (($layout eq "third") ? 3 : 1))} @PHILOSOPHERS),
                layout => $layout,
              },
              t => $threads, #threads
            ),
            "philosophers"
          );
          push @runs, {name => $name, program => $program};
        }
      }

      @runs;
    },

    backoff => sub {
      my @runs;

      for my $threads (@THREADS) {
        for my $layout ("alternating") {
          for my $backoff (
            [minBackoff => 0, maxBackoff => 0, factorBackoff => 0],
            [minBackoff => 100000, maxBackoff => 10000000, factorBackoff => 1.0],
            [minBackoff => 100000, maxBackoff => 10000000, factorBackoff => 1.1],
            [minBackoff => 100000, maxBackoff => 10000000, factorBackoff => 1.15],
            [minBackoff => 100000, maxBackoff => 10000000, factorBackoff => 1.2],
            [minBackoff => 100000, maxBackoff => 10000000, factorBackoff => 1.3],) {
            my $name = "threads-$threads-layout-$layout-backoff". join "-", @$backoff;
            my $program = makeRunString("backoff", $name,
              fromBaseConfig(
                p => { # parameters
                  tableType => 'static',
                  engineName => "parrp",
                  philosophers => 16,
                  layout => $layout,
                  @$backoff
                },
                t => $threads, #threads
                wi => 5, # warmup iterations
                f => 1, # forks
                i => 5, # iterations
              ),
              "philosophers"
            );
            push @runs, {name => $name, program => $program};
          }
        }
      }

      @runs;
    },

    dynamicPhilosophers => sub {
      my @runs;

      for my $threads (@THREADS) {
        for my $layout (@LAYOUTS) {
          my $name = "threads-$threads-layout-$layout-dynamic";
          my $program = makeRunString("dynamicPhilosophers", $name,
            fromBaseConfig(
              p => { # parameters
                tableType => 'dynamic',
                engineName => (join ',', @ENGINES),
                philosophers => (join ',', grep {$_ >= ($threads * (($layout eq "third") ? 3 : 1))} @PHILOSOPHERS),
                layout => $layout,
              },
              t => $threads, #threads
            ),
            "philosophers"
          );
          push @runs, {name => $name, program => $program};
        }
      }

      @runs;
    },

    dynamicStacks => sub {
      my @runs;

      for my $threads (@THREADS) {
        for my $steps (@STEPS) {

          my $name = "threads-$threads-steps-$steps";
          my $program = makeRunString("dynamicStacks", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
                work => 0,
                size => 10,
                steps => $steps,
              },
              t => $threads, #threads
            ),
            "dynamic.Stacks"
          );
          push @runs, {name => $name, program => $program};
        }
      }
      @runs;
    },

    expensiveConflict => sub {
      my @runs;

      for my $work (0,100,500,1000,3000,5000,7500,10000) {
        my $name = "work-$work";
        my $program = makeRunString("expensiveConflict", $name,
          fromBaseConfig(
            p => { # parameters
              engineName => (join ',', @ENGINES),
              work => $work,
            },
            t => 2, #threads
          ),
          "ExpensiveConflict"
        );
        push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    reference => sub {
      my @runs;

      for my $size (@THREADS) {
          my $name = "threads-$size";
          my $program = makeRunString("reference", $name,
            fromBaseConfig(
              p => { # parameters
                work => 2000,
              },
              t => $size, #threads
            ),
            "WorkReference"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    singleDynamic => sub {
      my @runs;

      for my $steps (@STEPS) {
          my $name = "steps-$steps";
          my $program = makeRunString("singleDynamic", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
                step =>  $steps
              },
            ),
            "dynamic.SingleSwitch"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    singleVar => sub {
      my @runs;

      for my $threads (@THREADS) {
          my $name = "threads-$threads";
          my $program = makeRunString("singleVar", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
              },
              t => $threads,
            ),
            "simple.SingleVar"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    turnCreation => sub {
      my @runs;

      for my $threads (@THREADS) {
          my $name = "threads-$threads";
          my $program = makeRunString("turnCreation", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
              },
              t => $threads,
            ),
            "simple.TurnCreation"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    creation => sub {
      my @runs;

      for my $threads (@THREADS) {
          my $name = "threads-$threads";
          my $program = makeRunString("creation", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
              },
              t => $threads,
            ),
            "simple.Creation"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },


    simpleChain => sub {
      my @runs;

      for my $size (@SIZES) {
          my $name = "size-$size";
          my $program = makeRunString("simpleChain", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
                size => $size,
              },
              t => 1,
            ),
            "benchmarks.simple.Chain"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },

    simpleFan => sub {
      my @runs;

      for my $size (@SIZES) {
          my $name = "size-$size";
          my $program = makeRunString("simpleFan", $name,
            fromBaseConfig(
              p => { # parameters
                engineName => (join ',', @ENGINES),
                size => $size,
              },
              t => 1,
            ),
            "benchmarks.simple.Fan"
          );
          push @runs, {name => $name, program => $program};
      }

      @runs;
    },



    # stmbank => sub {
    #   my @runs;

    #   for my $size (@THREADS) {
    #     for my $chance ("0.01", "0.001", "0") {
    #       my $name = "threads-$size-$chance";
    #       my $program = makeRunString("stmbank", $name,
    #         fromBaseConfig(
    #           p => { # parameters
    #             riname => (join ',', @FRAMEWORKS),
    #             numberOfAccounts => 256,
    #             globalReadChance => $chance,
    #           },
    #           t => $size, #threads
    #         ),
    #         "STMBank.BankAccounts"
    #       );
    #       push @runs, {name => $name, program => $program};
    #     }
    #   }

    #   @runs;
    # },

  };
}


sub hhlrjob {
  my ($name, $programstring) = @_;
  return  <<ENDPROGRAM;
#!/bin/sh
# Job name
#BSUB -J REScalaBenchmark
#
# File / path where STDOUT will be written, the %J is the job id
#BSUB -o $OUTDIR/$name-%J.out
#
# File / path where STDERR will be written, the %J is the job id
# #BSUB -e $OUTDIR/$name-%J.err
#
# Request the time you need for execution in [hour:]minute
#BSUB -W $BSUB_TIME
#
# Required resources
#BSUB -R "$BSUB_REQUIRE"
#
# Request vitual memory you need for your job in MB
#BSUB -M 2048
#
# Request the number of compute slots you want to use
#BSUB -n $BSUB_CORES
#BSUB -q $BSUB_QUEUE
# request exclusive access
#BSUB -x

module unload openmpi
module load java
echo "--------- processors ------------------------"
cat /proc/cpuinfo
echo "--------- java version ----------------------"
java -version
echo "---------------------------------------------"

rm -r /tmp/\$(whoami)
mkdir /tmp/\$(whoami)
export LANG=en_US.UTF-8
export JAVA_OPTS="-Xmx1024m -Xms1024m -Djava.io.tmpdir=/tmp/\$(whoami)"
$programstring
rm -r /tmp/\$(whoami)

ENDPROGRAM
}
