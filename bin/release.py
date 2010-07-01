#!/usr/bin/python

"""
After building prebake, can be run to replace the reports, documentation,
and jars with the newly built versions.
"""

import filecmp
import os
import pipes

# mapping of paths of built trees to paths of snapshot trees
build_to_release = (
  ('code/out/doc', '../snapshot/doc'),
  ('code/out/reports', '../snapshot/reports'),
  ('code/out/prebake.zip', '../snapshot/prebake.zip'),
)

FILE = 'f'
DIR = 'd'
NO_EXIST = 'n'

def sync(src_to_dest):
  """
  Syncrhonize the destination file tree with the source file tree
  in both the current client and in subversion.
  """

  def classify(path):
    if not os.path.exists(path): return NO_EXIST
    if os.path.isdir(path): return DIR
    return FILE

  # If we see a case where (conflict) is present, then we need to be
  # sure to do svn deletes in a separate commit before svn adds.
  conflict = False
  # Keep track of changes to make in subversion
  svn_adds = []
  svn_deletes = []

  # A bunch of actions that can be taken to synchronize one aspect
  # of a source file and a destination file
  def run(argv):
    """
    Prints out a command line that needs to be run.
    """
    print ' '.join([pipes.quote(arg) for arg in argv])

  def svn(verb_and_flags, args):
    cmd = ['svn']
    cmd.extend(verb_and_flags)
    cmd.extend(args)
    run(cmd)

  def remove(src, dst): run(['rm', dst])

  def svn_delete(src, dst): svn_deletes.append(dst)

  def recurse(src, dst):
    children = set()
    if os.path.isdir(src): children.update(os.listdir(src))
    if os.path.isdir(dst):
      children.update(os.listdir(dst))
    children.discard('.svn')
    for child in children:
      handle(os.path.join(src, child), os.path.join(dst, child))

  def copy(src, dst): run(['cp', '-f', src, dst])

  def copy_if_different(src, dst):
    if not filecmp.cmp(src, dst, shallow=0): copy(src, dst)

  def svn_add(src, dst): svn_adds.append(dst)

  def cnf(src, dst): conflict = True

  def mkdir(src, dst): run(['mkdir', dst])

  # The below table contains the actions to take for each possible
  # scenario.
  actions = {
  # src        dst        actions
    (NO_EXIST, NO_EXIST): (),
    (NO_EXIST, FILE)    : (remove, svn_delete,),
    (NO_EXIST, DIR)     : (recurse, remove, svn_delete,),
    (FILE,     NO_EXIST): (copy, svn_add,),
    (FILE,     FILE)    : (copy_if_different,),
    (FILE,     DIR)     : (recurse, remove, svn_delete, copy, svn_add, cnf),
    (DIR,      NO_EXIST): (mkdir, svn_add, recurse,),
    (DIR,      FILE)    : (remove, svn_delete, mkdir, svn_add, recurse, cnf),
    (DIR,      DIR)     : (recurse,),
    }

  # Walk the file tree (see recurse action above) and synchronize it at
  # each step.
  def handle(src, dst):
    src_t = classify(src)
    dst_t = classify(dst)
    for action in actions[(src_t, dst_t)]: action(src, dst)

  for (src, dst) in build_to_release:
    handle(src, dst)

  if len(svn_deletes):
    svn(['delete'], svn_deletes)
    if conflict: 
      svn(['commit', '-m', 'remove obsolete files from the snapshot tree'],
          commit_args)
  if len(svn_adds):
    svn(['add', '--depth=empty'], svn_adds)

if '__main__' == __name__:
  sync(build_to_release)
