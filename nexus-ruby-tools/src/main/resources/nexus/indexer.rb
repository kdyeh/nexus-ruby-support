require 'rubygems/user_interaction'
# let's live without builder gem
module Builder
  module XChar
    def self.encode string
      string
    end
  end
end

require 'rubygems/indexer'
require 'fileutils'

# make newer gems work as well
class Gem::Specification
  def default_gem?( *args )
    false
  end
end
module Nexus
  class Indexer < Gem::Indexer

    def initialize( directory = ".", options = {} )
      require 'tmpdir'
      unless Dir.respond_to? :tmpdir=
        def Dir.tmpdir
          dir = "#{@directory}/tmp"
          FileUtils.mkdir_p( dir )
          dir
        end  
        def Dir.tmpdir=( dir )
          @directory = dir
        end
      end
      Dir.tmpdir = directory
      super
    end
    
    def build_marshal_gemspecs
      count = Gem::Specification.count { |s| not s.default_gem? }
      progress = ui.progress_reporter( count,
                                       "Generating Marshal quick index gemspecs for #{count} gems",
                                       "Complete" )

      files = []

      Gem.time 'Generated Marshal quick index gemspecs' do
        Gem::Specification.each do |spec|
          next if spec.default_gem?
          spec_file_name = "#{spec.original_name}.gemspec.rz"
          # produce nested directory structure with first character of gemname
          marshal_name = File.join( @quick_marshal_dir, 
                                    spec_file_name[0],
                                    spec_file_name )
          
          marshal_zipped = Gem.deflate Marshal.dump(spec)
          FileUtils.mkdir_p( File.dirname( marshal_name ) )
          open marshal_name, 'wb' do |io| io.write marshal_zipped end
          
          files << marshal_name
          
          progress.updated spec.original_name
        end
        
        progress.done
      end
      
      @files << @quick_marshal_dir
      
      files
    end
    
    def gem_file_list
p Dir[File.join(@dest_directory, "gems", '*', '*.gem')]
p File.join(@dest_directory, "gems", '*', '*.gem')
      Dir[File.join(@dest_directory, "gems", '*', '*.gem')]
    end

    def remove_tmp_dir
      FileUtils.rm_rf( File.dirname( @directory ) )
    end
  end
end
