# Generated by jeweler
# DO NOT EDIT THIS FILE DIRECTLY
# Instead, edit Jeweler::Tasks in rakefile, and run the gemspec command
# -*- encoding: utf-8 -*-

Gem::Specification.new do |s|
  s.name = %q{always_verify_ssl_certificates}
  s.version = "0.3.0"

  s.required_rubygems_version = Gem::Requirement.new(">= 0") if s.respond_to? :required_rubygems_version=
  s.authors = ["James Golick"]
  s.date = %q{2011-03-17}
  s.description = %q{Ruby’s net/http is setup to never verify SSL certificates by default. Most ruby libraries do the same. That means that you’re not verifying the identity of the server you’re communicating with and are therefore exposed to man in the middle attacks. This gem monkey-patches net/http to force certificate verification and make turning it off impossible.}
  s.email = %q{jamesgolick@gmail.com}
  s.extra_rdoc_files = [
    "LICENSE",
     "README.rdoc"
  ]
  s.files = [
    ".document",
     ".gitignore",
     "LICENSE",
     "README.rdoc",
     "Rakefile",
     "VERSION",
     "always_verify_ssl_certificates.gemspec",
     "lib/always_verify_ssl_certificates.rb",
     "test/helper.rb",
     "test/test_always_verify_ssl_certificates.rb"
  ]
  s.homepage = %q{http://github.com/jamesgolick/always_verify_ssl_certificates}
  s.rdoc_options = ["--charset=UTF-8"]
  s.require_paths = ["lib"]
  s.rubygems_version = %q{1.5.2}
  s.summary = %q{Force net/http to always verify SSL certificates.}
  s.test_files = [
    "test/helper.rb",
     "test/test_always_verify_ssl_certificates.rb"
  ]

  if s.respond_to? :specification_version then
    s.specification_version = 3

    if Gem::Version.new(Gem::VERSION) >= Gem::Version.new('1.2.0') then
    else
    end
  else
  end
end

